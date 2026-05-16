package dev.tvbili.ui.home.components

/** 把秒数格式化为 "m:ss" 或 "h:mm:ss"。0 / 负数返回空串。 */
fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val s = seconds % 60
    val m = (seconds / 60) % 60
    val h = seconds / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** 把播放量 / 在线人数格式化为 "1.2万 / 350万 / 2.5亿"。 */
fun formatCount(count: Long): String = when {
    count < 10_000 -> count.toString()
    count < 100_000_000 -> "%.1f万".format(count / 10_000.0)
    else -> "%.1f亿".format(count / 100_000_000.0)
}

fun formatCountInt(count: Int): String = formatCount(count.toLong())
