use sjturunningman::route::{self, Point};
use tiny_skia::{Color, Paint, PathBuilder, Pixmap, Stroke, Transform};

pub fn render(points: &[Point], dark: bool) -> slint::Image {
    let mut pixmap = Pixmap::new(900, 650).expect("Fixed preview size");
    pixmap.fill(if dark {
        Color::from_rgba8(38, 60, 48, 255)
    } else {
        Color::from_rgba8(234, 241, 232, 255)
    });
    let mut grid = Paint::default();
    grid.set_color_rgba8(
        if dark { 54 } else { 214 },
        if dark { 78 } else { 225 },
        if dark { 62 } else { 212 },
        130,
    );
    for x in (0..900).step_by(45) {
        let mut p = PathBuilder::new();
        p.move_to(x as f32, 0.0);
        p.line_to(x as f32, 650.0);
        if let Some(path) = p.finish() {
            pixmap.stroke_path(
                &path,
                &grid,
                &Stroke::default(),
                Transform::identity(),
                None,
            );
        }
    }
    for y in (0..650).step_by(45) {
        let mut p = PathBuilder::new();
        p.move_to(0.0, y as f32);
        p.line_to(900.0, y as f32);
        if let Some(path) = p.finish() {
            pixmap.stroke_path(
                &path,
                &grid,
                &Stroke::default(),
                Transform::identity(),
                None,
            );
        }
    }
    if points.len() > 1 {
        let min_x = points.iter().map(|p| p.lon).fold(f64::INFINITY, f64::min);
        let max_x = points
            .iter()
            .map(|p| p.lon)
            .fold(f64::NEG_INFINITY, f64::max);
        let min_y = points.iter().map(|p| p.lat).fold(f64::INFINITY, f64::min);
        let max_y = points
            .iter()
            .map(|p| p.lat)
            .fold(f64::NEG_INFINITY, f64::max);
        let aspect = points[0].lat.to_radians().cos().abs().max(0.1);
        let scale =
            (720.0 / ((max_x - min_x) * aspect).max(1e-8)).min(460.0 / (max_y - min_y).max(1e-8));
        let project = |p: Point| {
            (
                (450.0 + (p.lon - (min_x + max_x) / 2.0) * aspect * scale) as f32,
                (325.0 - (p.lat - (min_y + max_y) / 2.0) * scale) as f32,
            )
        };
        for segment in points.windows(2) {
            let (x1, y1) = project(segment[0]);
            let (x2, y2) = project(segment[1]);
            let mut p = PathBuilder::new();
            p.move_to(x1, y1);
            p.line_to(x2, y2);
            let mut paint = Paint::default();
            if route::haversine(segment[0], segment[1]) > 150.0 {
                paint.set_color_rgba8(207, 99, 81, 255);
            } else if dark {
                paint.set_color_rgba8(157, 231, 165, 255);
            } else {
                paint.set_color_rgba8(35, 104, 73, 255);
            }
            let stroke = Stroke {
                width: 5.0,
                line_cap: tiny_skia::LineCap::Round,
                ..Default::default()
            };
            if let Some(path) = p.finish() {
                pixmap.stroke_path(&path, &paint, &stroke, Transform::identity(), None);
            }
        }
        for (point, red) in [(points[0], false), (*points.last().unwrap(), true)] {
            let (x, y) = project(point);
            let mut outer = Paint::default();
            outer.set_color_rgba8(255, 255, 255, 255);
            if let Some(path) = PathBuilder::from_circle(x, y, 11.0) {
                pixmap.fill_path(
                    &path,
                    &outer,
                    tiny_skia::FillRule::Winding,
                    Transform::identity(),
                    None,
                );
            }
            let mut paint = Paint::default();
            if red {
                paint.set_color_rgba8(207, 99, 81, 255);
            } else {
                paint.set_color_rgba8(35, 104, 73, 255);
            }
            if let Some(path) = PathBuilder::from_circle(x, y, 7.0) {
                pixmap.fill_path(
                    &path,
                    &paint,
                    tiny_skia::FillRule::Winding,
                    Transform::identity(),
                    None,
                );
            }
        }
    }
    // tiny-skia 使用预乘 alpha；这里背景完全不透明，可直接交给 Slint。
    let buffer =
        slint::SharedPixelBuffer::<slint::Rgba8Pixel>::clone_from_slice(pixmap.data(), 900, 650);
    slint::Image::from_rgba8(buffer)
}

/// 验证码图片来自网络，字节数有上限不代表解码后内存可控，尺寸限制见 `api::decode_captcha`。
pub fn decode(bytes: &[u8]) -> Result<slint::Image, String> {
    let image = sjturunningman::api::decode_captcha(bytes)?.into_rgba8();
    Ok(slint::Image::from_rgba8(
        slint::SharedPixelBuffer::clone_from_slice(image.as_raw(), image.width(), image.height()),
    ))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn captcha_decoding_is_bounded_and_rejects_junk() {
        let oversized = image::DynamicImage::ImageRgb8(image::RgbImage::new(4_000, 8));
        let mut buffer = std::io::Cursor::new(Vec::new());
        oversized
            .write_to(&mut buffer, image::ImageFormat::Png)
            .unwrap();
        assert!(decode(&buffer.into_inner()).is_err());
        assert!(decode(b"not an image").is_err());
    }
}
