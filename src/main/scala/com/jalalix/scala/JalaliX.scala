package com.jalalix.scala

import java.time._
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAccessor
import java.util.Locale
import java.util.regex.Pattern
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks.{break, breakable}

/** Options for JalaliX.create / parseJalali / normalizeQueryDate. */
final case class JalaliOptions(
  timezone: Option[String] = None,
  jalaliYearMin: Option[Int] = None,
  jalaliYearMax: Option[Int] = None
)

/** Options for JalaliX.format. */
final case class JalaliFormatOptions(calendar: Option[String] = None, persianDigits: Boolean = false)

/**
 * Jalali (Persian / Shamsi) calendar on a UTC millisecond instant + IANA ZoneId.
 * Pure Scala / java.time port of @jalalix/typescript - same algorithm and camelCase API.
 */
final class JalaliX private (private var time: Long, private var timezone: String) {

  private class WallParts {
    var year, month, day, hour, minute, second, ms = 0
  }

  private class JalaliDate {
    var year = 1348
    var month = 10
    var day = 11
    var hours, min, sec, ms = 0
    var isLeapYear = false
  }

  private val jalali = new JalaliDate
  private var isConverted = false
  private val am = "ق.ظ."
  private val pm = "ب.ظ."
  private val amGregorian = "AM"
  private val pmGregorian = "PM"

  def this() = this(System.currentTimeMillis(), "UTC")
  def this(epochMs: Long) = this(epochMs, "UTC")
  def this(isoOrDate: String) = this(JalaliX.parseInput(isoOrDate), "UTC")
  def this(instant: Instant) = this(instant.toEpochMilli, "UTC")

  private def zoneId: ZoneId = ZoneId.of(timezone)

  private def getWallParts: WallParts = {
    val z = Instant.ofEpochMilli(time).atZone(zoneId)
    val w = new WallParts
    w.year = z.getYear; w.month = z.getMonthValue; w.day = z.getDayOfMonth
    w.hour = z.getHour; w.minute = z.getMinute; w.second = z.getSecond
    w.ms = Math.floorMod(time, 1000).toInt
    w
  }

  private def wallToUtc(y: Int, m: Int, d: Int, h: Int, mi: Int, s: Int, ms: Int): Long = {
    val want = JalaliX.dateUtc(y, m, d, h, mi, s)
    var utc = want
    val zone = zoneId

    breakable {
      for (_ <- 0 until 14) {
        val parts = Instant.ofEpochMilli(utc).atZone(zone)
        val got = JalaliX.dateUtc(parts.getYear, parts.getMonthValue, parts.getDayOfMonth, parts.getHour, parts.getMinute, parts.getSecond)
        val diff = want - got
        if (diff == 0) break()
        utc += diff
      }
    }

    (utc / 1000) * 1000 + Math.floorMod(ms, 1000)
  }

  private def toJalali: JalaliDate = {
    if (!isConverted) {
      val wall = getWallParts
      val civilDay = JalaliX.dateUtc(wall.year, wall.month, wall.day, 0, 0, 0) / JalaliX.DATE_LENGTH
      val baseDay = JalaliX.dateUtc(1970, 1, 1, 0, 0, 0) / JalaliX.DATE_LENGTH
      val diffDays = civilDay - baseDay
      jalali.year = 1348; jalali.month = 10; jalali.day = 11

      if (diffDays > 0) {
        var i = 0L
        while (i < diffDays) {
          jalali.day += 1

          if (jalali.month < 7 && jalali.day > 31) { jalali.month += 1; jalali.day = 1 }
          else if (jalali.month > 6 && jalali.month < 12 && jalali.day > 30) { jalali.month += 1; jalali.day = 1 }
          else if (jalali.month == 12) {
            val leap = checkLeapYear(jalali.year)

            if ((leap && jalali.day > 30) || (!leap && jalali.day > 29)) {
              jalali.year += 1; jalali.month = 1; jalali.day = 1
            }
          }

          i += 1
        }
      } else if (diffDays < 0) {
        var i = 0L

        while (i < -diffDays) {
          jalali.day -= 1

          if (jalali.day < 1) {
            if (jalali.month == 1) {
              jalali.year -= 1; jalali.month = 12
              jalali.day = if (checkLeapYear(jalali.year)) 30 else 29
            } else if (jalali.month < 8) { jalali.month -= 1; jalali.day = 31 }
            else if (jalali.month < 13) { jalali.month -= 1; jalali.day = 30 }
          }

          i += 1
        }
      }

      jalali.hours = wall.hour; jalali.min = wall.minute; jalali.sec = wall.second; jalali.ms = wall.ms
      jalali.isLeapYear = checkLeapYear(jalali.year)
      isConverted = true
    }

    jalali
  }

  private def toGregorian(jDate: JalaliDate): Long = {
    val cur = new JalaliDate
    var diffDays = 0L

    val after = jDate.year > 1348 || (jDate.year == 1348 && jDate.month >= 10) ||
      (jDate.year == 1348 && jDate.month == 10 && jDate.day >= 11)

    val before = jDate.year < 1348 || (jDate.year == 1348 && jDate.month < 10) ||
      (jDate.year == 1348 && jDate.month == 10 && jDate.day < 11)

    if (after) {
      while (!(cur.year == jDate.year && cur.month == jDate.month && cur.day == jDate.day)) {
        diffDays += 1; cur.day += 1

        if (cur.month < 7 && cur.day > 31) { cur.month += 1; cur.day = 1 }
        else if (cur.month > 6 && cur.month < 12 && cur.day > 30) { cur.month += 1; cur.day = 1 }
        else if (cur.month == 12) {
          val leap = checkLeapYear(cur.year)

          if ((leap && cur.day > 30) || (!leap && cur.day > 29)) {
            cur.year += 1; cur.month = 1; cur.day = 1
          }
        }
      }
    } else if (before) {
      while (!(cur.year == jDate.year && cur.month == jDate.month && cur.day == jDate.day)) {
        diffDays -= 1; cur.day -= 1

        if (cur.day < 1) {
          if (cur.month == 1) {
            cur.year -= 1; cur.month = 12
            cur.day = if (checkLeapYear(cur.year)) 30 else 29
          } else if (cur.month < 8) { cur.month -= 1; cur.day = 31 }
          else if (cur.month < 13) { cur.month -= 1; cur.day = 30 }
        }
      }
    }

    val wallAsUtc = diffDays * JalaliX.DATE_LENGTH +
      (jDate.hours * 3600L + jDate.min * 60L + jDate.sec) * 1000L + jDate.ms

    val asUtc = Instant.ofEpochMilli(wallAsUtc).atZone(ZoneOffset.UTC)
    wallToUtc(asUtc.getYear, asUtc.getMonthValue, asUtc.getDayOfMonth, asUtc.getHour, asUtc.getMinute, asUtc.getSecond, asUtc.getNano / 1000000)
  }

  private def checkLeapYear(year: Int): Boolean =
    if (year > 0) {
      val checkYear = (year + 2346) * JalaliX.LEAP_YEAR_DIVISION
      checkYear - checkYear.toLong < JalaliX.LEAP_YEAR_DIVISION
    } else false

  private def reset(): JalaliDate = {
    isConverted = false
    jalali.year = 1348; jalali.month = 10; jalali.day = 11
    toJalali
  }

  private def addZero(number: Int): String = (if (number > -1 && number < 10) "0" else "") + number
  private def getJalaliMonthName(number: Int) = JalaliX.MONTHS_NAMES(number - 1)
  private def getGregorianMonthName(number: Int) = JalaliX.MONTHS_GREGORIAN_NAMES(number - 1)

  private def getWallWeekday: Int = {
    val wall = getWallParts
    Instant.ofEpochMilli(JalaliX.dateUtc(wall.year, wall.month, wall.day, 0, 0, 0))
      .atZone(ZoneOffset.UTC).getDayOfWeek.getValue % 7
  }

  private def getGregorianDayOfYear: Int = {
    val wall = getWallParts
    val start = JalaliX.dateUtc(wall.year, 1, 1, 0, 0, 0) - JalaliX.DATE_LENGTH
    val civil = JalaliX.dateUtc(wall.year, wall.month, wall.day, 0, 0, 0)
    Math.round((civil - start).toDouble / JalaliX.DATE_LENGTH).toInt
  }

  private def getGregorianWeekOfYear: Int = {
    val wall = getWallParts
    var d = Instant.ofEpochMilli(JalaliX.dateUtc(wall.year, wall.month, wall.day, 0, 0, 0)).atZone(ZoneOffset.UTC)
    val utcDay = d.getDayOfWeek.getValue % 7
    val adjust = 4 - (if (utcDay == 0) 7 else utcDay)
    d = d.plusDays(adjust.toLong)
    val yearStart = LocalDate.of(d.getYear, 1, 1).atStartOfDay(ZoneOffset.UTC)
    Math.ceil(((d.toInstant.toEpochMilli - yearStart.toInstant.toEpochMilli).toDouble / JalaliX.DATE_LENGTH + 1) / 7.0).toInt
  }

  private def getGregorianWeekDayName = JalaliX.WEEK_DAYS_GREGORIAN_NAMES(getWallWeekday)
  private def getGregorianWeekDayLetter = JalaliX.WEEK_DAYS_GREGORIAN_LETTERS(getWallWeekday)
  private def getWeekDayName = JalaliX.WEEK_DAYS_NAMES((getDay + JalaliX.FIRST_DAY_OF_WEEK) % 7)
  private def getWeekDayLetter = JalaliX.WEEK_DAYS_LETTERS((getDay + JalaliX.FIRST_DAY_OF_WEEK) % 7)

  private def getUTCTimezone: String = {
    val wall = getWallParts
    val asUtc = JalaliX.dateUtc(wall.year, wall.month, wall.day, wall.hour, wall.minute, wall.second) + wall.ms
    val offsetMin = Math.round((asUtc - time) / 60000.0).toInt
    val sign = if (offsetMin >= 0) "+" else "-"
    val abs = Math.abs(offsetMin)
    s" GMT$sign${addZero(abs / 60)}${addZero(abs % 60)}"
  }

  private def normalizeDate(days: Int): Long = { time = time + days * JalaliX.DATE_LENGTH; time }

  private def normalizeJalali(jDate: JalaliDate, yearIn: Int, monthIn: Int, dayIn: Int): Long = {
    var year = yearIn; var month = monthIn; var day = dayIn
    if (month < 1) {
      val yearDiff = Math.ceil((1 - month) / 12.0).toInt
      year -= yearDiff; month = 12 * yearDiff + month
    } else if (month > 12) {
      val yearDiff = Math.floor((month - 1) / 12.0).toInt
      year += yearDiff; month = month - 12 * yearDiff
    }

    if (month > 6 && month < 12 && day > 30) day = 30
    else if (month == 12) {
      val leap = checkLeapYear(year)
      if (leap && day > 30) day = 30
      else if (!leap && day > 29) day = 29
    }

    val next = new JalaliDate
    next.year = year; next.month = month; next.day = day
    next.hours = jDate.hours; next.min = jDate.min; next.sec = jDate.sec; next.ms = jDate.ms
    time = toGregorian(next)
    time
  }

  private def normalizeTimezone(tz: String): String = {
    val lowered = tz.toLowerCase(Locale.ROOT)

    if (lowered == "default" || lowered == "local" || lowered == "system") return JalaliX.hostTimezone
    if (lowered == "utc" || lowered == "gmt") return "UTC"

    try ZoneId.of(tz).getId
    catch { case e: DateTimeException => throw new IllegalArgumentException(s"Invalid IANA timezone: $tz", e) }
  }

  override def toString: String = {
    val j = toJalali
    s"${getWeekDayName} ${addZero(j.day)} ${getJalaliMonthName(j.month)} ${addZero(j.year)} ${j.hours}:${addZero(j.min)}:${addZero(j.sec)}$getUTCTimezone"
  }

  def toDateString: String = {
    val j = toJalali
    s"${getWeekDayName} ${addZero(j.day)} ${getJalaliMonthName(j.month)} ${addZero(j.year)}"
  }

  def toLocaleDateString: String = { val j = toJalali; s"${addZero(j.year)}/${addZero(j.month)}/${addZero(j.day)}" }
  def toLocaleTimeString: String = { val j = toJalali; s"${j.hours % 12}:${addZero(j.min)}:${addZero(j.sec)} ${getMeridiem}" }

  def getFullYear: Int = toJalali.year
  def getMonth: Int = toJalali.month
  def getDate: Int = toJalali.day
  def getDay: Int = (getWallWeekday - JalaliX.FIRST_DAY_OF_WEEK + 7 + 1) % 7
  def getHours: Int = getWallParts.hour
  def getMinutes: Int = getWallParts.minute
  def getSeconds: Int = getWallParts.second
  def getMilliseconds: Int = Math.floorMod(time, 1000).toInt
  def getTime: Long = time

  def setTime(t: Long): Long = { time = t; reset(); getTime }

  def setMilliseconds(ms: Int): Long = {
    val wall = getWallParts
    time = wallToUtc(wall.year, wall.month, wall.day, wall.hour, wall.minute, wall.second, ms)
    reset(); getTime
  }

  def setSeconds(sec: Int): Long = setSeconds(sec, getWallParts.ms)

  def setSeconds(sec: Int, ms: Int): Long = {
    val wall = getWallParts
    time = wallToUtc(wall.year, wall.month, wall.day, wall.hour, wall.minute, sec, ms)
    reset(); getTime
  }

  def setMinutes(min: Int): Long = { val w = getWallParts; setMinutes(min, w.second, w.ms) }
  def setMinutes(min: Int, sec: Int): Long = setMinutes(min, sec, getWallParts.ms)

  def setMinutes(min: Int, sec: Int, ms: Int): Long = {
    val wall = getWallParts
    time = wallToUtc(wall.year, wall.month, wall.day, wall.hour, min, sec, ms)
    reset(); getTime
  }

  def setHours(hours: Int): Long = { val w = getWallParts; setHours(hours, w.minute, w.second, w.ms) }
  def setHours(hours: Int, min: Int): Long = { val w = getWallParts; setHours(hours, min, w.second, w.ms) }
  def setHours(hours: Int, min: Int, sec: Int): Long = setHours(hours, min, sec, getWallParts.ms)

  def setHours(hours: Int, min: Int, sec: Int, ms: Int): Long = {
    val wall = getWallParts
    time = wallToUtc(wall.year, wall.month, wall.day, hours, min, sec, ms)
    reset(); getTime
  }

  def setDate(date: Int): Long = { val j = toJalali; normalizeDate(date - j.day); reset(); getTime }

  def setMonth(month: Int): Long = setMonth(month, None)

  def setMonth(month: Int, date: Option[Int]): Long = {
    var j = toJalali
    normalizeJalali(j, j.year, month + 1, j.day); reset()
    date.foreach { d => j = toJalali; normalizeDate(d - j.day); reset() }
    getTime
  }

  def setMonth(month: Int, date: Int): Long = setMonth(month, Some(date))

  def setFullYear(year: Int): Long = setFullYear(year, None, None)
  def setFullYear(year: Int, month: Int): Long = setFullYear(year, Some(month), None)
  def setFullYear(year: Int, month: Int, date: Int): Long = setFullYear(year, Some(month), Some(date))

  def setFullYear(year: Int, month: Option[Int], date: Option[Int]): Long = {
    var j = toJalali
    normalizeJalali(j, year, month.map(_ + 1).getOrElse(j.month), j.day); reset()
    date.foreach { d => j = toJalali; normalizeDate(d - j.day); reset() }
    getTime
  }

  override def clone(): JalaliX = new JalaliX(time, timezone) // TS API name; not Object.clone
  def getTimezone: String = timezone

  def getWeekOfYear: Int = {
    val dayOfYear = getDayOfYear
    val dayOfWeek = new JalaliX(time).setTimezone(timezone).getStartOfYear.getDayISO
    Math.ceil((dayOfYear + dayOfWeek - 1) / 7.0).toInt
  }

  def getDayOfYear: Int = {
    val j = toJalali

    if (j.month < 7) j.day + (j.month - 1) * 31
    else if (j.month < 12) j.day + 186 + (j.month - 7) * 30
    else j.day + 336
  }

  def getDayISO: Int = ((getDay + 6) % 7) + 1
  def getHoursISO: Int = { val h = getHours; if (h > 12) h - 12 else h }
  def getMeridiem: String = if (toJalali.hours > 12) pm else am

  private def getGregorianMeridiem: String = if (getHours >= 12) pmGregorian else amGregorian

  def getStartOfYear: JalaliX = addDays(-1 * getDayOfYear + 1).getStartOfDay
  def getStartOfMonth: JalaliX = addDays(-1 * getDate + 1).getStartOfDay
  def getStartOfWeek: JalaliX = addDays(-1 * getDayISO + 1).getStartOfDay
  def getStartOfDay: JalaliX = { setHours(0, 0, 0, 0); this }

  def getEndOfYear: JalaliX = {
    val lastDay = if (toJalali.isLeapYear) 366 else 365
    addDays(lastDay - getDayOfYear).getEndOfDay
  }

  def getEndOfMonth: JalaliX = {
    val j = toJalali
    var lastDay = 31

    if (j.month > 6 && j.month < 12) lastDay = 30
    else if (j.month == 12) lastDay = if (j.isLeapYear) 30 else 29

    addDays(lastDay - getDate).getEndOfDay
  }

  def getEndOfWeek: JalaliX = addDays(7 - getDayISO).getEndOfDay
  def getEndOfDay: JalaliX = { setHours(23, 59, 59, 999); this }

  def setTimezone(tz: String): JalaliX = new JalaliX(getTime, normalizeTimezone(tz))

  def addYears(year: Int): JalaliX = { val j = toJalali; normalizeJalali(j, j.year + year, j.month, j.day); reset(); this }
  def addMonths(month: Int): JalaliX = { val j = toJalali; normalizeJalali(j, j.year, j.month + month, j.day); reset(); this }
  def addWeeks(weeks: Int): JalaliX = { normalizeDate(weeks * 7); reset(); this }
  def addDays(days: Int): JalaliX = { normalizeDate(days); reset(); this }

  def toISODate: String = toISODate("gregorian")

  def toISODate(calendar: String): String = {
    if (calendar == "jalali") format("YYYY-MM-DD")
    else { val wall = getWallParts; s"${wall.year}-${addZero(wall.month)}-${addZero(wall.day)}" }
  }

  def format(str: String): String = format(str, persianDigits = false, gregorian = false)
  def format(str: String, persianDigits: Boolean): String = format(str, persianDigits, gregorian = false)

  def format(str: String, options: JalaliFormatOptions): String =
    format(str, options.persianDigits, options.calendar.contains("gregorian"))

  def format(str: String, persianDigits: Boolean, gregorian: Boolean): String = {
    val placeholders = ArrayBuffer.empty[String]
    def ph(v: String): String = { placeholders += v; s"\uE000${placeholders.length - 1}\uE001" }
    var output = str
    val wall = getWallParts
    val year = if (gregorian) wall.year else getFullYear
    val month = if (gregorian) wall.month else getMonth
    val weekOfYear = if (gregorian) getGregorianWeekOfYear else getWeekOfYear
    val dayOfYear = if (gregorian) getGregorianDayOfYear else getDayOfYear
    val date = if (gregorian) wall.day else getDate
    val monthName = if (gregorian) getGregorianMonthName(month) else getJalaliMonthName(month)
    val weekDayName = if (gregorian) getGregorianWeekDayName else getWeekDayName
    val weekDayLetter = if (gregorian) getGregorianWeekDayLetter else getWeekDayLetter
    val meridiem = if (gregorian) getGregorianMeridiem else getMeridiem
    val yearStr = year.toString

    if (output.contains("YYYY")) output = output.replace("YYYY", ph(yearStr))
    if (output.contains("YYY")) output = output.replace("YYY", ph(yearStr.takeRight(3)))
    if (output.contains("YY")) output = output.replace("YY", ph(yearStr.takeRight(2)))
    if (output.contains("Y")) output = output.replace("Y", ph(yearStr.takeRight(1)))
    if (output.contains("MMM")) output = output.replace("MMM", ph(monthName))
    if (output.contains("MM")) output = output.replace("MM", ph(addZero(month)))
    if (output.contains("M")) output = output.replace("M", ph(month.toString))
    if (output.contains("W")) output = output.replace("W", ph(addZero(weekOfYear)))
    if (output.contains("w")) output = output.replace("w", ph(weekOfYear.toString))
    if (output.contains("DW")) output = output.replace("DW", ph(getDayISO.toString))

    if (output.contains("dw")) {
      val dw = if (gregorian) (if (getWallWeekday == 0) 7 else getWallWeekday) else getDay
      output = output.replace("dw", ph(dw.toString))
    }

    if (output.contains("WN")) output = output.replace("WN", ph(weekDayName))
    if (output.contains("wn")) output = output.replace("wn", ph(weekDayName))
    if (output.contains("WL")) output = output.replace("WL", ph(weekDayLetter))
    if (output.contains("wl")) output = output.replace("wl", ph(weekDayLetter))
    if (output.contains("DDDD")) output = output.replace("DDDD", ph(addZero(dayOfYear)))
    if (output.contains("DDD")) output = output.replace("DDD", ph(dayOfYear.toString))
    if (output.contains("DD")) output = output.replace("DD", ph(addZero(date)))
    if (output.contains("D")) output = output.replace("D", ph(date.toString))

    val hours = getHours

    if (output.contains("HH")) output = output.replace("HH", ph(addZero(hours)))
    if (output.contains("H")) output = output.replace("H", ph(hours.toString))

    val hoursISO = if (hours > 12) hours - 12 else if (hours == 0) 12 else hours

    if (output.contains("hh")) output = output.replace("hh", ph(addZero(hoursISO)))
    if (output.contains("h")) output = output.replace("h", ph(hoursISO.toString))
    if (output.contains("mm")) output = output.replace("mm", ph(addZero(getMinutes)))
    if (output.contains("m")) output = output.replace("m", ph(getMinutes.toString))
    if (output.contains("ss")) output = output.replace("ss", ph(addZero(getSeconds)))
    if (output.contains("s")) output = output.replace("s", ph(getSeconds.toString))
    if (output.contains("sss")) output = output.replace("sss", ph(getMilliseconds.toString))
    if (output.contains("SSS")) output = output.replace("SSS", ph(getMilliseconds.toString))
    if (output.contains("A")) output = output.replace("A", ph(meridiem))
    if (output.contains("a")) output = output.replace("a", ph(meridiem))
    if (output.contains("X")) output = output.replace("X", ph((getTime / 1000).toString))
    if (output.contains("x")) output = output.replace("x", ph(getTime.toString))

    placeholders.zipWithIndex.foreach { case (v, i) => output = output.replace(s"\uE000$i\uE001", v) }

    if (persianDigits) JalaliX.toPersian(output) else output
  }
}

object JalaliX {
  val persianNumbers: Array[String] = Array("۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹")
  val arabicNumbers: Array[String] = Array("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
  private[jalalix] val LEAP_YEAR_DIVISION = 0.24219858156
  private[jalalix] val FIRST_DAY_OF_WEEK = 6
  private[jalalix] val DATE_LENGTH = 86400000L
  private val JALALI_PARSE = Pattern.compile("^(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})(?:[ T](\\d{1,2}):(\\d{1,2})(?::(\\d{1,2}))?)?$")
  private val DATE_ONLY = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$")
  private val NAN_PATTERN = Pattern.compile("NaN", Pattern.CASE_INSENSITIVE)
  private val FRACTIONAL_SECONDS_PATTERN = Pattern.compile("(\\d{2}:\\d{2}:\\d{2})\\.\\d+")
  private val NORMALIZE_QUERY_DATE_PATTERN = Pattern.compile(
    "^(\\d{4})[./-](\\d{1,2})[./-](\\d{1,2})(?:[ T](\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?:Z|[+-]\\d{2}:?\\d{2})?)?"
  )
  private[jalalix] val WEEK_DAYS_NAMES = Array("شنبه", "یکشنبه", "دوشنبه", "سه شنبه", "چهار شنبه", "پنج شنبه", "جمعه")
  private[jalalix] val WEEK_DAYS_LETTERS = Array("ش", "ی", "د", "س", "چ", "پ", "ج")
  private[jalalix] val WEEK_DAYS_GREGORIAN_NAMES = Array("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
  private[jalalix] val WEEK_DAYS_GREGORIAN_LETTERS = Array("S", "M", "T", "W", "T", "F", "S")
  private[jalalix] val MONTHS_NAMES = Array("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
  private[jalalix] val MONTHS_GREGORIAN_NAMES = Array("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

  private[jalalix] def hostTimezone: String = ZoneId.systemDefault.getId
  private[jalalix] def dateUtc(y: Int, m: Int, d: Int, h: Int, mi: Int, s: Int): Long =
    LocalDateTime.of(y, m, d, h, mi, s).toInstant(ZoneOffset.UTC).toEpochMilli

  private def parseIsoString(value: String): Long = {
    val s = value.trim

    if (DATE_ONLY.matcher(s).matches) {
      return LocalDate.parse(s).atStartOfDay(ZoneOffset.UTC).toInstant.toEpochMilli
    }

    try {
      if (s.endsWith("Z") || s.endsWith("z")) return Instant.parse(s).toEpochMilli

      if (s.contains("T")) {
        try return Instant.parse(s).toEpochMilli
        catch { case _: DateTimeParseException => }
        try return java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(s, Instant.from _).toEpochMilli
        catch { case _: DateTimeParseException => }
        return LocalDateTime.parse(s).atZone(ZoneId.systemDefault).toInstant.toEpochMilli
      }

      Instant.parse(s).toEpochMilli
    } catch {
      case e: DateTimeParseException => throw new IllegalArgumentException(s"""JalaliX.create: invalid date string "$value"""", e)
    }
  }

  private[jalalix] def parseInput(date: Any): Long = date match {
    case n: java.lang.Number => n.longValue
    case n: Long => n
    case n: Int => n.toLong
    case j: JalaliX => j.getTime
    case i: Instant => i.toEpochMilli
    case t: TemporalAccessor => Instant.from(t).toEpochMilli
    case s: String => parseIsoString(s)
    case _ => throw new IllegalArgumentException("JalaliX.create: unsupported date type")
  }

  def create(date: Any): JalaliX = create(date, None)
  def create(date: Any, options: JalaliOptions): JalaliX = create(date, Some(options))

  def create(date: Any, options: Option[JalaliOptions]): JalaliX = {
    var output = new JalaliX(parseInput(date))
    options.flatMap(_.timezone).foreach { tz => output = output.setTimezone(tz) }
    output
  }

  def parseJalali(value: String): JalaliX = parseJalali(value, None)
  def parseJalali(value: String, options: JalaliOptions): JalaliX = parseJalali(value, Some(options))

  def parseJalali(value: String, options: Option[JalaliOptions]): JalaliX = {
    val normalized = fromPersian(value).trim
    val match_ = JALALI_PARSE.matcher(normalized)

    if (!match_.matches) throw new IllegalArgumentException(s"""JalaliX.parseJalali: invalid Jalali date "$value"""")

    val year = match_.group(1).toInt
    val month = match_.group(2).toInt
    val day = match_.group(3).toInt
    val hours = Option(match_.group(4)).map(_.toInt).getOrElse(0)
    val minutes = Option(match_.group(5)).map(_.toInt).getOrElse(0)
    val seconds = Option(match_.group(6)).map(_.toInt).getOrElse(0)

    if (month < 1 || month > 12 || day < 1 || day > 31)
      throw new IllegalArgumentException(s"""JalaliX.parseJalali: invalid Jalali date "$value"""")

    val output = create(System.currentTimeMillis(): Any, options)
    output.setFullYear(year, month - 1, day)
    output.setHours(hours, minutes, seconds, 0)
    output
  }

  def isValidDate(date: Any): Boolean = date match {
    case _: JalaliX | _: Instant | _: java.lang.Number | _: Long | _: Int => true
    case t: TemporalAccessor => try { Instant.from(t); true } catch { case _: Exception => false }
    case _ => false
  }

  def toPersian(str: String): String = {
    var out = str
    for (i <- 0 to 9) out = out.replace(i.toString, persianNumbers(i))
    out
  }

  /** Converts Persian (۰-۹) and Arabic-Indic (٠-٩) numerals to Western numerals (0-9). */
  def fromPersian(str: String): String = {
    var out = str
    for (i <- 0 to 9) {
      out = out.replace(persianNumbers(i), i.toString)
      out = out.replace(arabicNumbers(i), i.toString)
    }
    out
  }

  def compare(date: JalaliX, comparing: JalaliX): Boolean = date.getTime < comparing.getTime

  /**
   * Soft-normalizes loose query / form date inputs into a Gregorian `YYYY-MM-DD HH:mm:ss` string.
   *
   * Handles empty/sentinel values (`null`, `"undefined"`, `"Invalid Date"`, `"NaN"`), unix
   * timestamps (seconds or milliseconds), Persian/Arabic digits, `/` `-` `.` separators,
   * fractional seconds, optional `Z` / offset suffixes, and auto-detects Jalali vs Gregorian by
   * year band: years in `jalaliYearMin..jalaliYearMax` (default `1..1899`) are treated as Jalali
   * when the civil date is valid; otherwise Gregorian.
   *
   * Invalid or unrecognized values are returned cleaned (digits normalized) rather than throwing;
   * sentinels and non-positive timestamps return `null`. Non-string / non-numeric values pass
   * through unchanged.
   */
  def normalizeQueryDate(value: Any): Any = normalizeQueryDate(value, None)
  def normalizeQueryDate(value: Any, options: JalaliOptions): Any = normalizeQueryDate(value, Some(options))

  def normalizeQueryDate(value: Any, options: Option[JalaliOptions]): Any = value match {
    case null => null
    case s: String =>
      val isInvalidDate = s.equalsIgnoreCase("Invalid Date") || NAN_PATTERN.matcher(s).find()

      if (s == "null" || s.isEmpty || s == "undefined" || isInvalidDate) {
        null
      } else {
        parseNumeric(s.trim) match {
          case Some(ts) => normalizeQueryTimestamp(ts, options)
          case None => normalizeQueryDateString(s, options)
        }
      }
    case n: java.lang.Number => normalizeQueryTimestamp(n.longValue, options)
    case other => other
  }

  private def parseNumeric(s: String): Option[Long] =
    scala.util.Try(s.toLong).toOption.orElse(scala.util.Try(s.toDouble.toLong).toOption)

  private def normalizeQueryTimestamp(timestampIn: Long, options: Option[JalaliOptions]): Any = {
    val timestamp = if (timestampIn > 9999999999L) timestampIn / 1000L else timestampIn

    if (timestamp <= 0) {
      null
    } else {
      create(timestamp * 1000L: Any, options)
        .format("YYYY-MM-DD HH:mm:ss", JalaliFormatOptions(calendar = Some("gregorian")))
    }
  }

  private def normalizeQueryDateString(value: String, options: Option[JalaliOptions]): Any = {
    val digitsRaw = fromPersian(value)
    val digits = FRACTIONAL_SECONDS_PATTERN.matcher(digitsRaw).replaceAll("$1")

    val m = NORMALIZE_QUERY_DATE_PATTERN.matcher(digits)

    if (!m.find()) {
      digits
    } else {
      val year = m.group(1).toInt
      val month = m.group(2).toInt
      val day = m.group(3).toInt
      val hour = Option(m.group(4)).map(_.toInt).getOrElse(0)
      val minute = Option(m.group(5)).map(_.toInt).getOrElse(0)
      val second = Option(m.group(6)).map(_.toInt).getOrElse(0)

      val jalaliYearMin = options.flatMap(_.jalaliYearMin).getOrElse(1)
      val jalaliYearMax = options.flatMap(_.jalaliYearMax).getOrElse(1899)

      if (year >= jalaliYearMin && year <= jalaliYearMax && isValidJalaliCivilDate(year, month, day)) {
        try {
          val jalaliStr = f"$year%04d/$month%02d/$day%02d $hour%02d:$minute%02d:$second%02d"

          parseJalali(jalaliStr, options).format("YYYY-MM-DD HH:mm:ss", JalaliFormatOptions(calendar = Some("gregorian")))
        } catch {
          case _: IllegalArgumentException => gregorianOrDigits(year, month, day, hour, minute, second, digits)
        }
      } else {
        gregorianOrDigits(year, month, day, hour, minute, second, digits)
      }
    }
  }

  private def gregorianOrDigits(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, digits: String): Any = {
    if (isValidGregorianDate(year, month, day)) {
      f"$year%04d-$month%02d-$day%02d $hour%02d:$minute%02d:$second%02d"
    } else {
      digits
    }
  }

  /** Whether Y/M/D is a valid Jalali civil date (year >= 1). */
  private def isValidJalaliCivilDate(year: Int, month: Int, day: Int): Boolean = {
    if (year < 1 || month < 1 || month > 12 || day < 1) false
    else if (month <= 6) day <= 31
    else if (month <= 11) day <= 30
    else {
      val isLeap = new JalaliX(0L).checkLeapYear(year)
      day <= (if (isLeap) 30 else 29)
    }
  }

  private def isValidGregorianDate(year: Int, month: Int, day: Int): Boolean =
    try {
      LocalDate.of(year, month, day)
      true
    } catch {
      case _: DateTimeException => false
    }
}
