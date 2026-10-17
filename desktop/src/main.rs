#![cfg_attr(
    all(target_os = "windows", not(debug_assertions)),
    windows_subsystem = "windows"
)]

mod preview;

use sjturunningman::{
    api::{LoginChallenge, LoginResult, Session},
    maps::MapServer,
    route::{self, Route},
    settings::{self, RunConfig, Settings},
    task::{self, Event, Preview},
    update,
};
use slint::{ComponentHandle, ModelRc, VecModel};
use std::{
    cell::RefCell,
    path::{Path, PathBuf},
    rc::Rc,
    sync::{
        atomic::{AtomicBool, Ordering},
        mpsc, Arc,
    },
    time::{Duration, Instant},
};
slint::include_modules!();

enum AppEvent {
    Challenge(Result<PreparedLogin, String>),
    Login(Result<LoginResult, String>, String),
    CodeSent(Result<(), String>),
    Verified(Result<(), String>),
    Imported(Result<Option<String>, String>),
    Update(Result<String, String>),
    Task(Event),
    TaskError(String),
}

struct PreparedLogin {
    challenge: LoginChallenge,
    captcha: Result<String, String>,
}

type PreviewInputs = (String, String, String, String);

fn preview_inputs(ui: &AppWindow) -> PreviewInputs {
    (
        ui.get_runs().into(),
        ui.get_distance().into(),
        ui.get_run_date().into(),
        ui.get_run_time().into(),
    )
}

/// 参数改过之后旧预览不再对应当前设置，此时按「没有预览」处理，避免地图和摘要
/// 显示上一条参数的轨迹。
fn fresh_preview(ui: &AppWindow, state: &mut State) -> Option<Preview> {
    if state.preview_inputs.as_ref() == Some(&preview_inputs(ui)) {
        state.preview.clone()
    } else {
        state.preview = None;
        state.preview_inputs = None;
        None
    }
}

struct State {
    dir: PathBuf,
    maps: Option<MapServer>,
    map_output: Option<PathBuf>,
    routes: Vec<Route>,
    preview: Option<Preview>,
    /// 生成 preview 时的参数（次数、距离、日期、时间），用于判断预览是否已过期。
    preview_inputs: Option<PreviewInputs>,
    session: Option<Session>,
    challenge: Option<LoginChallenge>,
    two_factor: Option<Session>,
    user: String,
    cancel: Arc<AtomicBool>,
    close_after_stop: bool,
    verification_attempts: u8,
}

fn dispatch(sender: &mpsc::Sender<AppEvent>, ui: &slint::Weak<AppWindow>, event: AppEvent) {
    if sender.send(event).is_ok() {
        let _ = ui.upgrade_in_event_loop(|ui| ui.invoke_receive_events());
    }
}

/// 系统里可能带 CJK 字形的字体文件，(家族名, 路径)，按优先级排列。
fn cjk_font_candidates() -> &'static [(&'static str, &'static str)] {
    if cfg!(target_os = "windows") {
        &[
            ("Microsoft YaHei UI", r"C:\Windows\Fonts\msyh.ttc"),
            ("SimHei", r"C:\Windows\Fonts\simhei.ttf"),
        ]
    } else if cfg!(target_os = "macos") {
        &[
            ("PingFang SC", "/System/Library/Fonts/PingFang.ttc"),
            (
                "Hiragino Sans GB",
                "/System/Library/Fonts/Hiragino Sans GB.ttc",
            ),
            ("Heiti SC", "/System/Library/Fonts/STHeiti Medium.ttc"),
            ("Arial Unicode MS", "/Library/Fonts/Arial Unicode.ttf"),
        ]
    } else {
        &[
            (
                "Noto Sans CJK SC",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            ),
            (
                "Noto Sans CJK SC",
                "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
            ),
            (
                "WenQuanYi Zen Hei",
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
            ),
        ]
    }
}

/// 选界面字体：先把系统里的 CJK 字体文件注册进 Slint 自己的字体库，再把它选为默认
/// 家族，并追加为汉字（Hani）的 fallback。
///
/// 只写系统字体名是不够的：macOS 上 `"PingFang SC"` 在部分环境（如 CI 的无人会话）
/// 解析不到，界面里中文会整段不渲染、只剩数字和拉丁字母。注册文件后家族一定存在，
/// 汉字也不依赖系统字体枚举。
///
/// 返回 (界面字体家族, 诊断信息)，诊断信息会写进 `--smoke-test` 的报告便于排查。
fn install_ui_font() -> (String, Vec<String>) {
    use slint::fontique_07::{fontique, shared_collection};
    let mut report = Vec::new();
    let mut chosen: Option<String> = None;
    for (family, path) in cjk_font_candidates() {
        let file = std::path::Path::new(path);
        if !file.exists() {
            report.push(format!("{family}: 未找到 {path}"));
            continue;
        }
        let bytes = match std::fs::read(file) {
            Ok(bytes) => bytes,
            Err(error) => {
                report.push(format!("{family}: 读取失败 {error}"));
                continue;
            }
        };
        let mut collection = shared_collection();
        let registered = collection.register_fonts(fontique::Blob::new(Arc::new(bytes)), None);
        if registered.is_empty() {
            report.push(format!("{family}: 注册未返回可用字体"));
            continue;
        }
        let ids: Vec<_> = registered.iter().map(|(id, _)| *id).collect();
        let appended = collection.append_fallbacks(
            fontique::FallbackKey::new("Hani", None),
            ids.iter().copied(),
        );
        report.push(format!(
            "{family}: 已注册 {} 个字体，汉字 fallback {}",
            ids.len(),
            if appended { "已追加" } else { "未生效" }
        ));
        if chosen.is_none() {
            chosen = Some((*family).to_owned());
        }
    }
    let family = chosen.unwrap_or_else(|| {
        let fallback = if cfg!(target_os = "windows") {
            "Microsoft YaHei UI"
        } else if cfg!(target_os = "macos") {
            "PingFang SC"
        } else {
            "Noto Sans CJK SC"
        };
        report.push(format!("未注册到任何字体，回退到系统字体名 {fallback}"));
        fallback.to_owned()
    });
    (family, report)
}

fn log(ui: &AppWindow, message: &str) {
    let old = ui.get_log_text();
    let text = format!(
        "{old}\n[{}] {message}",
        chrono::Local::now().format("%H:%M:%S")
    );
    // 限制内存日志，长批量任务不会无限累积。
    ui.set_log_text(
        text.lines()
            .rev()
            .take(250)
            .collect::<Vec<_>>()
            .into_iter()
            .rev()
            .collect::<Vec<_>>()
            .join("\n")
            .into(),
    );
}

fn notify(ui: &AppWindow, message: impl Into<String>) {
    let message = message.into();
    ui.set_notice(message.clone().into());
    log(ui, &message);
}

fn config(ui: &AppWindow) -> Result<RunConfig, String> {
    RunConfig::parse(
        &ui.get_runs(),
        &ui.get_distance(),
        &ui.get_run_date(),
        &ui.get_run_time(),
    )
}

fn selected(state: &State, ui: &AppWindow) -> Result<Route, String> {
    state
        .routes
        .get(ui.get_route_index().max(0) as usize)
        .cloned()
        .ok_or("请选择有效路线".into())
}

fn refresh_route(ui: &AppWindow, state: &mut State) {
    state.preview = None;
    state.preview_inputs = None;
    if let Ok(route) = selected(state, ui) {
        ui.set_route_image(preview::render(&route.points, ui.get_dark()));
        ui.set_route_title(route.name.into());
        ui.set_can_delete(route.path.is_some());
        ui.set_route_summary(
            format!(
                "原始长度 {:.2} km    /    {} 个坐标点",
                route::distance(&route.points) / 1_000.0,
                route.points.len()
            )
            .into(),
        );
        ui.set_preview_summary("原始路线 · 点击「预览实际轨迹」生成目标距离".into());
        let jumps = route::jumps(&route.points);
        ui.set_route_warning(if jumps > 0 {
            format!("发现 {jumps} 个超过 150 m 的跳段，请检查后再使用").into()
        } else {
            "".into()
        });
    }
}

fn show_preview(ui: &AppWindow, state: &mut State, data: Preview) {
    ui.set_route_image(preview::render(&data.points, ui.get_dark()));
    ui.set_preview_summary(
        format!(
            "实际轨迹 {:.2} km · {} 分 {:02} 秒 · {} 个点",
            data.distance_m / 1_000.0,
            data.duration_s / 60,
            data.duration_s % 60,
            data.points.len()
        )
        .into(),
    );
    state.preview = Some(data);
    state.preview_inputs = Some(preview_inputs(ui));
}

fn reload_routes(ui: &AppWindow, state: &mut State, name: &str) -> Result<(), String> {
    let (routes, skipped) = route::list(&state.dir)?;
    state.routes = routes;
    for problem in skipped {
        log(ui, &format!("跳过无法解析的路线：{problem}"));
    }
    ui.set_route_names(ModelRc::new(VecModel::from(
        state
            .routes
            .iter()
            .map(|r| r.name.clone().into())
            .collect::<Vec<slint::SharedString>>(),
    )));
    ui.set_route_index(
        state
            .routes
            .iter()
            .position(|r| r.name == name)
            .unwrap_or(0) as i32,
    );
    refresh_route(ui, state);
    Ok(())
}

fn open_map_page(state: &mut State, name: &str, html: String) -> Result<(), String> {
    if state.maps.is_none() {
        state.maps = Some(MapServer::start()?);
    }
    let url = state
        .maps
        .as_ref()
        .ok_or("地图服务未启动")?
        .publish(name, html)?;
    if let Some(dir) = &state.map_output {
        std::fs::write(dir.join(format!("{name}-url.txt")), url)
            .map_err(|_| "无法保存地图验证地址".into())
    } else {
        webbrowser::open(&url).map_err(|_| "无法打开系统浏览器".into())
    }
}

/// Release 使用 `panic = "abort"` 且 Windows 走 GUI 子系统，`eprintln!` 不会显示在
/// 任何地方；把 panic 落到用户数据目录，至少留下可排查的线索。
fn install_panic_log() {
    let path = settings::data_dir()
        .map(|dir| dir.join("crash.log"))
        .unwrap_or_else(|_| std::env::temp_dir().join("sjturunningman-crash.log"));
    std::panic::set_hook(Box::new(move |info| {
        if let Ok(previous) = std::fs::read_to_string(&path) {
            // 只保留最近几次崩溃，避免无限增长。
            let kept: Vec<_> = previous.lines().rev().take(100).collect();
            let text = kept.into_iter().rev().collect::<Vec<_>>().join("\n");
            let _ = std::fs::write(&path, format!("{text}\n{info}\n"));
        } else if let Some(dir) = path.parent() {
            let _ = std::fs::create_dir_all(dir);
            let _ = std::fs::write(&path, format!("{info}\n"));
        }
    }));
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    install_panic_log();
    let started = Instant::now();
    let args: Vec<_> = std::env::args_os().collect();
    let login_smoke = args.get(1).is_some_and(|s| s == "--login-smoke-test");
    let map_smoke = args.get(1).is_some_and(|s| s == "--map-smoke-test");
    let smoke = login_smoke || map_smoke || args.get(1).is_some_and(|s| s == "--smoke-test");
    let smoke_dir = smoke.then(|| {
        args.get(2)
            .map(PathBuf::from)
            .unwrap_or_else(|| PathBuf::from("target/smoke"))
    });
    let dir = if smoke {
        smoke_dir.as_ref().unwrap().join("data")
    } else {
        settings::data_dir()?
    };
    let (prefs, warning) = match settings::load(&dir) {
        Ok(p) => (p, None),
        Err(e) => (Settings::default(), Some(e)),
    };
    // 坏文件已被备份成 settings.json.bak 时 path 不再存在，此时照常保存；
    // 只有备份也失败（原文件仍在）才跳过保存，避免覆盖用户数据。
    let preserve_settings = warning.is_some() && dir.join("settings.json").exists();
    let (ui_font, font_report) = install_ui_font();
    let ui = AppWindow::new()?;
    ui.set_ui_font(ui_font.into());
    ui.set_dark(prefs.dark);
    ui.set_runs(prefs.runs.clone().into());
    ui.set_distance(prefs.distance.clone().into());
    ui.set_run_time(prefs.time.clone().into());
    ui.set_run_date(settings::yesterday().into());
    ui.set_version(format!("v{}", env!("APP_VERSION")).into());
    let state = Rc::new(RefCell::new(State {
        dir,
        maps: None,
        map_output: if map_smoke { smoke_dir.clone() } else { None },
        routes: Vec::new(),
        preview: None,
        preview_inputs: None,
        session: None,
        challenge: None,
        two_factor: None,
        user: String::new(),
        cancel: Arc::new(AtomicBool::new(false)),
        close_after_stop: false,
        verification_attempts: 0,
    }));
    if let Err(error) = reload_routes(&ui, &mut state.borrow_mut(), &prefs.route) {
        notify(&ui, error);
        let mut state = state.borrow_mut();
        state.routes = vec![Route {
            name: "default".into(),
            path: None,
            points: route::parse(route::DEFAULT_ROUTE)?,
        }];
        ui.set_route_names(ModelRc::new(VecModel::from(vec!["default".into()])));
        refresh_route(&ui, &mut state);
    }
    if let Some(warning) = warning {
        notify(&ui, warning);
    }
    let (sender, receiver) = mpsc::channel();

    let weak = ui.as_weak();
    let shared = state.clone();
    ui.on_receive_events(move || {
        let Some(ui) = weak.upgrade() else {
            return;
        };
        for event in receiver.try_iter() {
            let mut state = shared.borrow_mut();
            match event {
                AppEvent::Challenge(result) => {
                    ui.set_network_busy(false);
                    match result {
                        Ok(prepared) => match preview::decode(&prepared.challenge.image) {
                            Ok(image) => {
                                ui.set_captcha_image(image);
                                state.challenge = Some(prepared.challenge);
                                match prepared.captcha {
                                    Ok(code) => {
                                        ui.set_captcha(code.into());
                                        ui.set_manual_captcha(false);
                                        ui.set_notice("图形验证码已自动识别，填写账号和密码即可登录".into());
                                    }
                                    Err(error) => {
                                        ui.set_manual_captcha(true);
                                        notify(&ui, format!("自动识别暂不可用：{error}。可刷新重试，或手动填写备用验证码。"));
                                    }
                                }
                            }
                            Err(e) => notify(&ui, e),
                        },
                        Err(e) => notify(&ui, e),
                    }
                }
                AppEvent::Login(result, user) => {
                    ui.set_network_busy(false);
                    match result {
                        Ok(LoginResult::Authenticated(session)) => {
                            state.session = Some(session);
                            state.user = user;
                            ui.set_logged_in(true);
                            ui.set_login_stage(0);
                            notify(&ui, "登录成功，会话仅保留在内存中");
                        }
                        Ok(LoginResult::TwoFactor(session)) => {
                            state.two_factor = Some(session);
                            state.user = user;
                            state.verification_attempts = 0;
                            ui.set_login_stage(2);
                            ui.set_notice("请选择验证方式并发送验证码".into());
                        }
                        Err(e) => notify(&ui, format!("{e}；请点击刷新，重新自动识别后重试")),
                    }
                }
                AppEvent::CodeSent(result) => {
                    ui.set_network_busy(false);
                    notify(
                        &ui,
                        result
                            .map(|_| "验证码已发送，请查收".into())
                            .unwrap_or_else(|e| e),
                    );
                }
                AppEvent::Verified(result) => {
                    ui.set_network_busy(false);
                    match result {
                        Ok(()) => {
                            state.session = state.two_factor.take();
                            ui.set_logged_in(true);
                            ui.set_login_stage(0);
                            ui.set_two_factor_code("".into());
                            notify(&ui, "二次验证成功");
                        }
                        Err(e) => {
                            state.verification_attempts += 1;
                            notify(&ui, e);
                            if state.verification_attempts >= 5 {
                                state.two_factor = None;
                                state.user.clear();
                                ui.set_login_stage(0);
                                ui.set_two_factor_code("".into());
                                notify(&ui, "二次验证已达 5 次，请重新登录");
                            }
                        }
                    }
                }
                AppEvent::Imported(result) => {
                    ui.set_network_busy(false);
                    match result {
                        Ok(Some(name)) => {
                            if let Err(e) = reload_routes(&ui, &mut state, &name) {
                                notify(&ui, e);
                            } else {
                                notify(&ui, "路线已导入，源文件保持原样");
                            }
                        }
                        Ok(None) => {}
                        Err(e) => notify(&ui, e),
                    }
                }
                AppEvent::Update(result) => {
                    ui.set_network_busy(false);
                    ui.set_update_status(
                        result
                            .unwrap_or_else(|e| format!("检查更新失败：{e}"))
                            .into(),
                    );
                }
                AppEvent::Task(event) => match event {
                    Event::Log(text) => log(&ui, &text),
                    Event::Progress(value, text) => {
                        ui.set_progress(value);
                        ui.set_status(text.into());
                    }
                    Event::Preview(data) => show_preview(&ui, &mut state, data),
                    Event::Finished {
                        success,
                        failed,
                        stopped,
                    } => {
                        ui.set_busy(false);
                        // 结束或停止后不要停在中间值，否则进度条看起来像卡住了。
                        ui.set_progress(if stopped { 0 } else { 100 });
                        ui.set_status(
                            format!(
                                "{} · 成功 {success} 条，失败 {failed} 条",
                                if stopped {
                                    "任务已停止"
                                } else {
                                    "任务结束"
                                }
                            )
                            .into(),
                        );
                        log(&ui, &ui.get_status());
                        if state.close_after_stop {
                            let _ = slint::quit_event_loop();
                        }
                    }
                },
                AppEvent::TaskError(error) => {
                    ui.set_busy(false);
                    ui.set_progress(0);
                    ui.set_status("任务失败".into());
                    notify(&ui, error);
                    if state.close_after_stop {
                        let _ = slint::quit_event_loop();
                    }
                }
            }
        }
    });

    let weak = ui.as_weak();
    let shared = state.clone();
    ui.on_select_route(move |_| {
        if let Some(ui) = weak.upgrade() {
            refresh_route(&ui, &mut shared.borrow_mut());
        }
    });
    let weak = ui.as_weak();
    let shared = state.clone();
    ui.on_refresh_preview(move || {
        if let Some(ui) = weak.upgrade() {
            let mut state = shared.borrow_mut();
            if let Some(data) = fresh_preview(&ui, &mut state) {
                show_preview(&ui, &mut state, data);
            } else {
                refresh_route(&ui, &mut state);
            }
        }
    });
    let weak = ui.as_weak();
    let shared = state.clone();
    ui.on_preview(move || {
        let Some(ui) = weak.upgrade() else {
            return;
        };
        let result = (|| {
            let config = config(&ui)?;
            let route = selected(&shared.borrow(), &ui)?;
            task::generate(
                &route.points,
                &config,
                "preview",
                chrono::Utc::now().timestamp_millis(),
                &serde_json::json!({}),
            )
        })();
        match result {
            Ok(data) => {
                show_preview(&ui, &mut shared.borrow_mut(), data);
                notify(&ui, "离线预览已更新，未发送网络请求");
            }
            Err(e) => notify(&ui, e),
        }
    });
    let weak = ui.as_weak();
    let shared = state.clone();
    let tx = sender.clone();
    ui.on_import_route(move || {
        let Some(ui) = weak.upgrade() else {
            return;
        };
        if ui.get_busy() || ui.get_network_busy() {
            return;
        }
        ui.set_network_busy(true);
        let dir = shared.borrow().dir.clone();
        let tx = tx.clone();
        let weak = weak.clone();
        std::thread::spawn(move || {
            let result = rfd::FileDialog::new()
                .set_title("导入 BD09 路线")
                .add_filter("路线文本", &["txt"])
                .pick_file()
                .map(|path| route::import(&dir, &path))
                .transpose();
            dispatch(&tx, &weak, AppEvent::Imported(result));
        });
    });
    let weak = ui.as_weak();
    let shared = state.clone();
    ui.on_delete_route(move || {
        let Some(ui) = weak.upgrade() else {
            return;
        };
        if ui.get_busy() {
            return;
        }
        let mut state = shared.borrow_mut();
        let result = selected(&state, &ui)
            .and_then(|route| route::delete(&state.dir, &route))
            .and_then(|_| reload_routes(&ui, &mut state, "default"));
        match result {
            Ok(()) => notify(&ui, "已删除导入的路线副本"),
            Err(e) => notify(&ui, e),
        }
    });
    let weak = ui.as_weak();
    let shared = state.clone();
    ui.on_open_map(move || {
        let Some(ui) = weak.upgrade() else {
            return;
        };
        let result = (|| {
            let mut state = shared.borrow_mut();
            let route = selected(&state, &ui)?;
            let had_preview = state.preview.is_some();
            let points = match fresh_preview(&ui, &mut state) {
                Some(data) => data.points,
                None => {
                    if had_preview {
                        notify(
                            &ui,
                            "参数已变更，地图先显示原始路线；重新点击「预览实际轨迹」查看新轨迹",
                        );
                    }
                    route::corrected(&route.points)
                }
            };
            let html = route::preview_html(&points, &route.name);
            open_map_page(&mut state, "preview", html)
        })();
        if let Err(e) = result {
            notify(&ui, e);
        }
    });
    let weak = ui.as_weak();
    let shared = state.clone();
    ui.on_planner(move || {
        if let Some(ui) = weak.upgrade() {
            let result = open_map_page(
                &mut shared.borrow_mut(),
                "planner",
                include_str!("../../route_planner.html").into(),
            );
            if let Err(e) = result {
                notify(&ui, e);
            } else {
                notify(&ui, "规划器已打开：导出 TXT 后通过「导入 TXT」添加");
            }
        }
    });

    let weak = ui.as_weak();
    let shared = state.clone();
    let tx = sender.clone();
    ui.on_prepare_login(move || {
        let Some(ui) = weak.upgrade() else {
            return;
        };
        if ui.get_network_busy() || ui.get_busy() {
            return;
        }
        shared.borrow_mut().challenge = None;
        ui.set_login_stage(1);
        ui.set_captcha("".into());
        ui.set_manual_captcha(false);
        ui.set_captcha_image(slint::Image::default());
        ui.set_network_busy(true);
        ui.set_notice("正在加载并自动识别图形验证码…".into());
        let tx = tx.clone();
        let weak = weak.clone();
        std::thread::spawn(move || {
            let result = Session::prepare_login().map(|challenge| {
                let captcha = Session::recognize_captcha(&challenge.image);
                PreparedLogin { challenge, captcha }
            });
            dispatch(&tx, &weak, AppEvent::Challenge(result))
        });
    });
    let weak = ui.as_weak();
    let shared = state.clone();
    let tx = sender.clone();
    ui.on_submit_login(move || {
        let Some(ui) = weak.upgrade() else {
            return;
        };
        if ui.get_network_busy() {
            return;
        }
        let username = ui.get_username().trim().to_owned();
        let password = ui.get_password().to_string();
        let captcha = ui.get_captcha().to_string();
        if username.is_empty() || password.is_empty() {
            notify(&ui, "请填写账号和密码");
            return;
        }
        if captcha.trim().is_empty() {
            notify(
                &ui,
                "验证码尚未识别，请点击刷新重试；识别异常时也可手动填写",
            );
            return;
        }
        let Some(challenge) = shared.borrow_mut().challenge.take() else {
            notify(&ui, "请先刷新图形验证码");
            return;
        };
        ui.set_password("".into());
        ui.set_captcha("".into());
        ui.set_network_busy(true);
        let tx = tx.clone();
        let weak = weak.clone();
        std::thread::spawn(move || {
            let result = Session::login(challenge, &username, &password, &captcha);
            dispatch(&tx, &weak, AppEvent::Login(result, username));
        });
    });
    let weak = ui.as_weak();
    let shared = state.clone();
    ui.on_cancel_login(move || {
        if let Some(ui) = weak.upgrade() {
            let mut state = shared.borrow_mut();
            state.challenge = None;
            state.two_factor = None;
            state.user.clear();
            ui.set_login_stage(0);
            ui.set_password("".into());
            ui.set_captcha("".into());
            ui.set_two_factor_code("".into());
            ui.set_notice("".into());
        }
    });
    let weak = ui.as_weak();
    let shared = state.clone();
    let tx = sender.clone();
    ui.on_send_code(move || {
        let Some(ui) = weak.upgrade() else {
            return;
        };
        if ui.get_network_busy() {
            return;
        }
        let Some(session) = shared.borrow().two_factor.clone() else {
            return;
        };
        let method = ["app", "email", "sms"]
            .get(ui.get_two_factor_method().max(0) as usize)
            .copied()
            .unwrap_or("app");
        ui.set_network_busy(true);
        let tx = tx.clone();
        let weak = weak.clone();
        std::thread::spawn(move || {
            dispatch(&tx, &weak, AppEvent::CodeSent(session.send_code(method)))
        });
    });
    let weak = ui.as_weak();
    let shared = state.clone();
    let tx = sender.clone();
    ui.on_verify_code(move || {
        let Some(ui) = weak.upgrade() else {
            return;
        };
        if ui.get_network_busy() {
            return;
        }
        let Some(session) = shared.borrow().two_factor.clone() else {
            return;
        };
        let user = shared.borrow().user.clone();
        let code = ui.get_two_factor_code().to_string();
        ui.set_network_busy(true);
        let tx = tx.clone();
        let weak = weak.clone();
        std::thread::spawn(move || {
            dispatch(
                &tx,
                &weak,
                AppEvent::Verified(session.verify_code(&user, &code)),
            )
        });
    });
    let weak = ui.as_weak();
    let shared = state.clone();
    ui.on_logout(move || {
        if let Some(ui) = weak.upgrade() {
            let mut state = shared.borrow_mut();
            state.session = None;
            state.challenge = None;
            state.two_factor = None;
            state.user.clear();
            ui.set_username("".into());
            ui.set_logged_in(false);
            refresh_route(&ui, &mut state);
            notify(&ui, "已退出账号，内存会话已清除");
        }
    });

    let weak = ui.as_weak();
    let shared = state.clone();
    let tx = sender.clone();
    ui.on_start_task(move || {
        let Some(ui) = weak.upgrade() else {
            return;
        };
        if ui.get_busy() || ui.get_network_busy() || ui.get_consent_open() {
            return;
        }
        let config = match config(&ui) {
            Ok(c) => c,
            Err(e) => {
                notify(&ui, e);
                return;
            }
        };
        let mut state = shared.borrow_mut();
        let Some(session) = state.session.clone() else {
            notify(&ui, "请先登录账号");
            return;
        };
        let route = match selected(&state, &ui) {
            Ok(r) => r,
            Err(e) => {
                notify(&ui, e);
                return;
            }
        };
        state.cancel = Arc::new(AtomicBool::new(false));
        let cancel = state.cancel.clone();
        let user = state.user.clone();
        ui.set_busy(true);
        ui.set_progress(0);
        ui.set_notice("".into());
        ui.set_status("正在准备任务".into());
        let tx = tx.clone();
        let weak = weak.clone();
        std::thread::spawn(move || {
            if let Err(e) = task::run(&session, &route.points, &config, &user, &cancel, |event| {
                dispatch(&tx, &weak, AppEvent::Task(event))
            }) {
                dispatch(&tx, &weak, AppEvent::TaskError(e));
            }
        });
    });
    let weak = ui.as_weak();
    let shared = state.clone();
    ui.on_stop_task(move || {
        shared.borrow().cancel.store(true, Ordering::Relaxed);
        if let Some(ui) = weak.upgrade() {
            ui.set_status("正在停止，等待当前网络请求结束…".into());
        }
    });
    let weak = ui.as_weak();
    let tx = sender.clone();
    ui.on_check_update(move || {
        let Some(ui) = weak.upgrade() else {
            return;
        };
        if ui.get_network_busy() {
            return;
        }
        ui.set_network_busy(true);
        ui.set_update_status("正在检查…".into());
        let tx = tx.clone();
        let weak = weak.clone();
        std::thread::spawn(move || {
            dispatch(
                &tx,
                &weak,
                AppEvent::Update(update::check(env!("APP_VERSION"))),
            )
        });
    });
    let weak = ui.as_weak();
    ui.on_open_shuiyuan(move || {
        if webbrowser::open("https://shuiyuan.sjtu.edu.cn/t/topic/421786").is_err() {
            if let Some(ui) = weak.upgrade() {
                notify(&ui, "无法打开浏览器");
            }
        }
    });
    let weak = ui.as_weak();
    ui.on_open_project(move || {
        if webbrowser::open("https://github.com/JackyTJie/SJTURunningMan_Advanced_Edition").is_err()
        {
            if let Some(ui) = weak.upgrade() {
                notify(&ui, "无法打开浏览器");
            }
        }
    });
    let weak = ui.as_weak();
    ui.on_clear_log(move || {
        if let Some(ui) = weak.upgrade() {
            ui.set_log_text("".into());
        }
    });
    ui.on_quit(|| {
        let _ = slint::quit_event_loop();
    });
    let weak = ui.as_weak();
    let shared = state.clone();
    ui.window().on_close_requested(move || {
        if let Some(ui) = weak.upgrade() {
            if ui.get_busy() {
                let mut state = shared.borrow_mut();
                state.cancel.store(true, Ordering::Relaxed);
                state.close_after_stop = true;
                ui.set_status("正在停止任务，完成后退出…".into());
                return slint::CloseRequestResponse::KeepWindowShown;
            }
        }
        slint::CloseRequestResponse::HideWindow
    });
    ui.show()?;
    if let Some(dir) = smoke_dir {
        if map_smoke {
            std::fs::create_dir_all(&dir)?;
            ui.set_consent_open(false);
            ui.invoke_preview();
            ui.invoke_open_map();
            ui.invoke_planner();
        } else if login_smoke {
            std::fs::create_dir_all(&dir)?;
            ui.set_consent_open(false);
            ui.invoke_prepare_login();
            login_smoke_test(ui.as_weak(), dir, Instant::now());
        } else {
            smoke_test(&ui, &dir, started, font_report)?;
        }
    }
    slint::run_event_loop()?;
    if !smoke && !preserve_settings {
        let state = state.borrow();
        let settings = Settings {
            dark: ui.get_dark(),
            runs: ui.get_runs().into(),
            distance: ui.get_distance().into(),
            time: ui.get_run_time().into(),
            route: selected(&state, &ui)
                .map(|r| r.name)
                .unwrap_or_else(|_| "default".into()),
        };
        if let Err(error) = settings::save(&state.dir, &settings) {
            eprintln!("{error}");
        }
    }
    Ok(())
}

fn login_smoke_test(weak: slint::Weak<AppWindow>, dir: PathBuf, started: Instant) {
    slint::Timer::single_shot(Duration::from_millis(100), move || {
        let Some(ui) = weak.upgrade() else {
            return;
        };
        if ui.get_network_busy() && started.elapsed() < Duration::from_secs(45) {
            login_smoke_test(weak, dir, started);
            return;
        }
        let image = ui.get_captcha_image();
        let size = image.size();
        let recognized = !ui.get_captcha().is_empty() && !ui.get_manual_captcha();
        let success = recognized && size.width > 0 && size.height > 0 && !ui.get_network_busy();
        let report = serde_json::json!({
            "success":success,"image_width":size.width,"image_height":size.height,
            "automatically_recognized":recognized,"manual_input_visible":ui.get_manual_captcha(),
            "account_login_attempted":false,"elapsed_ms":started.elapsed().as_millis()
        });
        if std::fs::write(
            dir.join("login-smoke-result.json"),
            serde_json::to_vec_pretty(&report).unwrap(),
        )
        .is_err()
            || !success
        {
            eprintln!("Login smoke test failed: {}", ui.get_notice());
            std::process::exit(1);
        }
        // 验证图片已交给 Slint 后退出，不将真实验证码写进截图或日志。
        let _ = slint::quit_event_loop();
    });
}

fn smoke_test(
    ui: &AppWindow,
    dir: &Path,
    started: Instant,
    font_report: Vec<String>,
) -> Result<(), Box<dyn std::error::Error>> {
    std::fs::create_dir_all(dir)?;
    let dir = dir.to_owned();
    let weak = ui.as_weak();
    slint::Timer::single_shot(Duration::from_millis(300), move || {
        let Some(ui) = weak.upgrade() else {
            return;
        };
        let result = (|| -> Result<(), Box<dyn std::error::Error>> {
            save_snapshot(&ui, &dir.join("01-consent.png"))?;
            let shown_ms = started.elapsed().as_millis();
            ui.set_consent_checked(true);
            ui.set_consent_open(false);
            ui.invoke_preview();
            assert!(ui.get_preview_summary().contains("实际轨迹"));
            ui.set_runs("0".into());
            ui.invoke_preview();
            assert!(ui.get_notice().contains("1–30"));
            ui.set_runs("1".into());
            ui.invoke_preview();
            std::fs::write(
                dir.join("smoke-result.json"),
                serde_json::to_string_pretty(
                    &serde_json::json!({"window_ready_with_300ms_delay_ms":shown_ms,"preview":ui.get_preview_summary().as_str(),"input_validation":true,"version":env!("APP_VERSION"),"ui_font":ui.get_ui_font().as_str(),"font_candidates":font_report}),
                )?,
            )?;
            Ok(())
        })();
        if let Err(e) = result {
            eprintln!("Smoke test failed: {e}");
            std::process::exit(1);
        }
        smoke_pages(ui.as_weak(), dir, 0);
    });
    Ok(())
}

fn smoke_pages(weak: slint::Weak<AppWindow>, dir: PathBuf, index: usize) {
    use slint::platform::{Key, PointerEventButton, WindowEvent};
    let Some(ui) = weak.upgrade() else {
        return;
    };
    ui.set_notice("".into());
    match index {
        0..=15 => {
            let group = index / 4;
            let size = if group < 2 {
                (1120.0, 820.0)
            } else {
                (1040.0, 700.0)
            };
            ui.window()
                .set_size(slint::LogicalSize::new(size.0, size.1));
            ui.set_dark(group % 2 == 1);
            ui.set_page((index % 4) as i32);
        }
        16 => {
            ui.window().set_size(slint::LogicalSize::new(1120.0, 820.0));
            ui.set_dark(false);
            ui.set_page(0);
            ui.set_login_stage(1);
        }
        17 => ui.set_login_stage(2),
        18 => {
            ui.set_login_stage(0);
            ui.set_upload_open(true);
        }
        19 => {
            ui.set_upload_open(false);
            ui.set_delete_open(true);
        }
        20 => {
            ui.set_delete_open(false);
            ui.set_busy(true);
            ui.set_progress(45);
            ui.set_status("正在处理第 2 条记录，共 4 条".into());
        }
        21 => ui.set_page(3),
        22 => {
            ui.set_busy(false);
            ui.set_page(2);
            ui.invoke_clear_log();
            assert!(ui.get_log_text().is_empty());
        }
        23 => {
            ui.window().set_size(slint::LogicalSize::new(1040.0, 700.0));
            ui.set_log_text((0..40).map(|i| format!("[08:00:{:02}] 离线界面验证：这是用于检查长文本换行与滚动的测试日志，不会发起登录或上传请求。", i)).collect::<Vec<_>>().join("\n").into());
            ui.set_notice("这是一条用于验证窄窗口自动换行的提示信息。请选择合适的路线，并检查距离、起终点和路线连续性后继续。测试长提示应完整显示，并且不能遮挡下方日志卡片或右侧操作按钮。".into());
        }
        24 => {
            ui.window().set_size(slint::LogicalSize::new(1120.0, 820.0));
            ui.set_login_stage(2);
            ui.set_two_factor_method(0);
        }
        25 => {
            // 点击本进程窗口中的验证方式控件；不调用任何认证回调。
            let size = ui.window().size().to_logical(ui.window().scale_factor());
            let position = slint::LogicalPosition::new(
                (size.width - 500.0) / 2.0 + 100.0,
                (size.height - 365.0) / 2.0 + 140.0,
            );
            ui.window().dispatch_event(WindowEvent::PointerPressed {
                position,
                button: PointerEventButton::Left,
            });
            ui.window().dispatch_event(WindowEvent::PointerReleased {
                position,
                button: PointerEventButton::Left,
            });
        }
        26 => {
            let key = |key: Key| {
                let text: slint::SharedString = key.into();
                ui.window()
                    .dispatch_event(WindowEvent::KeyPressed { text: text.clone() });
                ui.window()
                    .dispatch_event(WindowEvent::KeyReleased { text });
            };
            key(Key::DownArrow);
            assert_eq!(
                ui.get_two_factor_method(),
                1,
                "Select must support keyboard selection after a mouse click"
            );
            key(Key::UpArrow);
            assert_eq!(ui.get_two_factor_method(), 0);
            key(Key::DownArrow);
            key(Key::Return);
            assert_eq!(ui.get_two_factor_method(), 1);
            ui.set_network_busy(true);
            key(Key::DownArrow);
            assert_eq!(
                ui.get_two_factor_method(),
                1,
                "Disabled Select must not change"
            );
            ui.set_network_busy(false);
        }
        _ => {
            let path = dir.join("smoke-result.json");
            let mut report: serde_json::Value =
                serde_json::from_slice(&std::fs::read(&path).unwrap()).unwrap();
            report["page_theme_size_combinations"] = 16.into();
            report["snapshot_count"] = 28.into();
            report["select_mouse_keyboard_disabled"] = true.into();
            report["clear_log"] = true.into();
            report["account_login_or_upload_attempted"] = false.into();
            std::fs::write(path, serde_json::to_vec_pretty(&report).unwrap()).unwrap();
            let _ = slint::quit_event_loop();
            return;
        }
    }
    slint::Timer::single_shot(Duration::from_millis(180), move || {
        let Some(ui) = weak.upgrade() else {
            return;
        };
        if let Err(e) = save_snapshot(&ui, &dir.join(format!("{:02}-screen.png", index + 2))) {
            eprintln!("Screenshot failed: {e}");
            std::process::exit(1);
        }
        smoke_pages(weak, dir, index + 1);
    });
}

fn save_snapshot(ui: &AppWindow, path: &Path) -> Result<(), Box<dyn std::error::Error>> {
    let pixels = ui.window().take_snapshot()?;
    // 快照的 alpha 通道实测未被填充（Windows 与 Linux 的 CI 产物都是全 0），
    // 直接存会让 PNG 在遵守 alpha 的查看器里整张透明、看起来像空白截图。
    // 窗口本身不透明，统一补成 255。
    let mut bytes = pixels.as_bytes().to_vec();
    for pixel in bytes.as_chunks_mut::<4>().0 {
        pixel[3] = 255;
    }
    image::save_buffer(
        path,
        &bytes,
        pixels.width(),
        pixels.height(),
        image::ColorType::Rgba8,
    )?;
    Ok(())
}

#[cfg(test)]
mod font_tests {
    use super::*;

    /// 注册流程本身要能在本机跑通：候选文件存在时，家族名必须能在 Slint 自己的
    /// 字体库里解析出来，否则界面又退回「中文不渲染」。
    #[test]
    fn registered_cjk_family_resolves_in_slints_collection() {
        let (family, report) = install_ui_font();
        assert!(
            report.iter().any(|line| line.contains("已注册")) || !report.is_empty(),
            "字体候选必须有诊断输出"
        );
        if report.iter().any(|line| line.contains("已注册")) {
            let mut collection = slint::fontique_07::shared_collection();
            assert!(
                collection.family_id(&family).is_some(),
                "注册后家族 {family} 应能解析，报告：{report:?}"
            );
        }
    }
}
