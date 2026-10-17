use semver::Version;
use serde_json::Value;
use std::time::Duration;

pub fn latest(items: &Value) -> Option<Version> {
    items
        .as_array()?
        .iter()
        .filter(|item| item["draft"] != true)
        .filter_map(|item| {
            Version::parse(item["tag_name"].as_str()?.trim_start_matches(['v', 'V'])).ok()
        })
        .max()
}

pub fn check(current: &str) -> Result<String, String> {
    let client = reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(8))
        .build()
        .map_err(crate::api::network_error)?;
    let value: Value=client.get("https://api.github.com/repos/JackyTJie/SJTURunningMan_Advanced_Edition/releases?per_page=30")
        .header("User-Agent",format!("SJTURunningMan/{current}"))
        .send().and_then(reqwest::blocking::Response::error_for_status).map_err(crate::api::network_error)?
        .json().map_err(crate::api::network_error)?;
    let remote = latest(&value).ok_or("未找到可用版本")?;
    let local = Version::parse(current.trim_start_matches('v')).map_err(|_| "本地版本无效")?;
    Ok(if remote > local {
        format!("发现新版本 v{remote}，可前往 GitHub 的 Releases 下载")
    } else {
        format!("当前 v{local} 已是最新版本")
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn release_selection_obeys_semver_and_skips_drafts() {
        let items = serde_json::json!([{"tag_name":"v4.5.0-beta.1"},{"tag_name":"v4.5.0"},{"tag_name":"v9.0.0","draft":true},{"tag_name":"invalid"}]);
        assert_eq!(latest(&items).unwrap(), Version::parse("4.5.0").unwrap());
        assert!(latest(&serde_json::json!([])).is_none());
    }
}
