package com.jalalix.scala

import java.nio.file.{Files, Paths}
import org.scalatest.funsuite.AnyFunSuite
import ujson._

/** Loads testdata/golden.json and asserts parity with the TypeScript contract. */
class GoldenTest extends AnyFunSuite {

  private val golden: Value = {
    val candidates = Seq(
      Paths.get("testdata/golden.json"),
      Paths.get("../testdata/golden.json")
    )
    val path = candidates.find(Files.exists(_)).getOrElse {
      throw new IllegalStateException("testdata/golden.json not found")
    }
    ujson.read(Files.readString(path))
  }

  private def createFrom(spec: Value): JalaliX = {
    if (spec.obj.contains("unixMs")) {
      var d = JalaliX.create(spec("unixMs").num.toLong)
      if (spec.obj.contains("timezone")) d = d.setTimezone(spec("timezone").str)
      d
    } else {
      JalaliX.create(spec("isoDate").str)
    }
  }

  test("calendar") {
    golden("calendar").arr.foreach { c =>
      val d = createFrom(c("create"))
      val expect = c("expect")
      val id = c("id").str
      assert(d.format("YYYY/MM/DD") == expect("formatYYYYMMDD").str, id)
      assert(d.toISODate == expect("toISODateGregorian").str, s"$id iso g")
      assert(d.toISODate("jalali") == expect("toISODateJalali").str, s"$id iso j")
    }
  }

  test("parse") {
    golden("parse").arr.foreach { c =>
      val d = JalaliX.parseJalali(c("input").str)
      val expect = c("expect")
      val id = c("id").str
      assert(d.toISODate == expect("toISODateGregorian").str, id)
      if (expect.obj.contains("formatYYYYMMDD")) {
        assert(d.format("YYYY/MM/DD") == expect("formatYYYYMMDD").str, s"$id format")
      }
    }
  }

  test("format") {
    golden("format").arr.foreach { c =>
      if (c.obj.contains("toPersian")) {
        assert(JalaliX.toPersian(c("toPersian")("input").str) == c("toPersian")("expect").str)
        assert(JalaliX.fromPersian(c("fromPersian")("input").str) == c("fromPersian")("expect").str)
      } else {
        val d = createFrom(c("create"))
        var calendar: Option[String] = None
        var persianDigits = false
        c.obj.get("options").foreach { options =>
          if (options.obj.contains("persianDigits")) persianDigits = options("persianDigits").bool
          if (options.obj.contains("calendar")) calendar = Some(options("calendar").str)
        }
        val opts = JalaliFormatOptions(calendar, persianDigits)
        assert(d.format(c("pattern").str, opts) == c("expect").str, c("id").str)
      }
    }
  }

  test("timezone") {
    golden("timezone").arr.foreach { c =>
      val id = c("id").str
      if (c.obj.contains("setTimezoneAlias")) {
        val d = JalaliX.create(c("unixMs").num.toLong).setTimezone(c("setTimezoneAlias").str)
        assert(d.getTimezone == c("expectTimezone").str, id)
      } else {
        val d = JalaliX.create(c("unixMs").num.toLong).setTimezone(c("withTimezone").str)
        val expect = c("expect")
        assert(d.format("YYYY/MM/DD") == expect("jalaliSlash").str, s"$id jalali")
        if (expect.obj.contains("jalaliIso")) {
          assert(d.toISODate("jalali") == expect("jalaliIso").str, s"$id jalaliIso")
        }
        if (expect.obj.contains("wallGregorian")) {
          assert(d.toISODate == expect("wallGregorian").str, s"$id wallG")
        }
        assert(d.getHours == expect("hours").num.toInt, s"$id h")
        assert(d.getMinutes == expect("minutes").num.toInt, s"$id m")
        if (expect.obj.contains("getTime")) {
          assert(d.getTime == expect("getTime").num.toLong, s"$id instant")
        }
      }
    }
  }

  test("mutations") {
    golden("mutations").arr.foreach { c =>
      var d = createFrom(c("create"))
      val id = c("id").str
      if (c.obj.contains("setFullYear")) {
        val sfy = c("setFullYear")
        d.setFullYear(sfy("year").num.toInt, sfy("month0").num.toInt, sfy("day").num.toInt)
        assert(d.getMonth == c("expect")("getMonth").num.toInt, s"$id getMonth")
        assert(d.format("YYYY/MM/DD") == c("expect")("formatYYYYMMDD").str, s"$id format")
      } else {
        c("ops").arr.foreach { opNode =>
          val op = opNode.str
          if (op.startsWith("addMonths:")) d = d.addMonths(op.split(":")(1).toInt)
          else if (op == "getStartOfMonth") d = d.getStartOfMonth
        }
        assert(d.format("YYYY/MM/DD") == c("expect")("formatYYYYMMDD").str, id)
      }
    }
  }

  test("smokeCompareClone") {
    val a = JalaliX.create("2025-02-25")
    val b = JalaliX.create("2025-02-26")
    assert(JalaliX.compare(a, b))
    val c = a.clone()
    assert(a.getTime == c.getTime)
    assert(a.getTimezone == c.getTimezone)
  }
}
