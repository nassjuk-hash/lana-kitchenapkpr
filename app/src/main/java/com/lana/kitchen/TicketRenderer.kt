package com.lana.kitchen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import org.json.JSONObject
import java.util.Locale

/**
 * يرسم تذكرة المطبخ بالعربي كصورة بعرض 80mm (576 نقطة)
 * بنفس تصميم الفاتورة الاحترافية v18 (برنامج الكمبيوتر): خط Cairo، شريط أسود للاسم والإجمالي،
 * فواصل متقطعة، صندوق كمية، ومحاذاة RTL كاملة.
 */
object TicketRenderer {
  private const val W = 576
  private const val X = 12f
  private const val CW = 552f

  private var cairoRegular: Typeface? = null
  private var cairoBold: Typeface? = null

  private fun fonts(ctx: Context): Pair<Typeface, Typeface> {
    if (cairoRegular == null || cairoBold == null) {
      cairoRegular = try { Typeface.createFromAsset(ctx.assets, "fonts/Cairo-Regular.ttf") } catch (_: Exception) { Typeface.DEFAULT }
      cairoBold = try { Typeface.createFromAsset(ctx.assets, "fonts/Cairo-Bold.ttf") } catch (_: Exception) { Typeface.DEFAULT_BOLD }
    }
    return Pair(cairoRegular!!, cairoBold!!)
  }

  private fun money(v: Double, cur: String): String {
    val label = if (cur == "JOD" || cur == "JD") "د.أ" else cur
    return String.format(Locale.US, "%.2f", v) + " " + label
  }

  private class Ctx(val c: Canvas, val reg: Typeface, val bold: Typeface) {
    var y = 12f

    fun paint(size: Float, isBold: Boolean, white: Boolean = false): TextPaint =
      TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (white) Color.WHITE else Color.BLACK
        textSize = size
        typeface = if (isBold) bold else reg
      }

    fun layout(text: String, p: TextPaint, width: Float, align: Layout.Alignment, rtl: Boolean): StaticLayout =
      StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt().coerceAtLeast(1))
        .setAlignment(align)
        .setTextDirection(if (rtl) TextDirectionHeuristics.FIRSTSTRONG_RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR)
        .setLineSpacing(0f, 1f)
        .build()

    /** نص بمحاذاة يمين (RTL) أو وسط */
    fun text(text: String, size: Float, isBold: Boolean, center: Boolean, spacing: Float = 6f, x: Float = X, width: Float = CW) {
      if (text.isBlank()) return
      val l = layout(text, paint(size, isBold), width, if (center) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL, true)
      c.save(); c.translate(x, y); l.draw(c); c.restore()
      y += l.height + spacing
    }

    /** شريط أسود بعرض الورقة مع نص أبيض بالوسط */
    fun band(text: String, size: Float, padding: Float = 8f) {
      if (text.isBlank()) return
      val l = layout(text, paint(size, true, white = true), CW, Layout.Alignment.ALIGN_CENTER, true)
      val bandH = l.height + padding * 2
      c.drawRect(0f, y, W.toFloat(), y + bandH, Paint().apply { color = Color.BLACK })
      c.save(); c.translate(X, y + padding); l.draw(c); c.restore()
      y += bandH + 8
    }

    /** خط فاصل متصل */
    fun rule(heavy: Boolean = false) {
      c.drawLine(0f, y + 5, (W - 1).toFloat(), y + 5, Paint().apply { color = Color.BLACK; strokeWidth = if (heavy) 3f else 1f })
      y += 16
    }

    /** خط فاصل متقطع */
    fun dashed() {
      c.drawLine(0f, y + 5, (W - 1).toFloat(), y + 5, Paint().apply {
        color = Color.BLACK; strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
      })
      y += 16
    }

    /** سطر «التسمية: القيمة» بمحاذاة اليمين */
    fun pair(label: String, value: String, strong: Boolean = true) {
      if (value.isBlank()) return
      text("$label: $value", if (strong) 21f else 20f, strong, center = false, spacing = 8f)
    }

    /** سطر صنف: صندوق كمية بإطار على اليمين واسم الصنف يليه */
    fun item(name: String, qty: String) {
      val l = layout(name, paint(24f, true), 480f, Layout.Alignment.ALIGN_NORMAL, true)
      val h = maxOf(52f, l.height + 14f)
      val box = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
      c.drawRect(504f, y + 4, 564f, y + 46, box)
      val q = layout(qty, paint(21f, true), 60f, Layout.Alignment.ALIGN_CENTER, false)
      c.save(); c.translate(504f, y + 8 + (38f - q.height) / 2); q.draw(c); c.restore()
      c.save(); c.translate(X, y + 6); l.draw(c); c.restore()
      y += h
    }
  }

  fun render(ctx: Context, ticket: JSONObject): Bitmap {
    val (reg, bold) = fonts(ctx)
    val probe = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    val measure = Canvas(probe)
    val g = Ctx(measure, reg, bold)

    val rest = ticket.getJSONObject("restaurant")
    val cur = rest.optString("currency", "JOD")

    val blocks = { g: Ctx ->
      g.band(rest.optString("name", "المطعم"), 40f, 10f)
      g.text("فاتورة طلب", 22f, true, center = true, spacing = 4f)
      val phone = rest.optString("phone", "")
      if (phone.isNotBlank()) g.text("هاتف: $phone", 17f, false, center = true, spacing = 6f)
      g.rule(heavy = true)

      if (ticket.optString("kind") == "test") {
        g.text("تجربة الطباعة", 30f, true, center = true, spacing = 8f)
        g.text("PRINTER TEST - ARABIC 80mm", 21f, true, center = true, spacing = 8f)
        g.dashed()
        val pr = ticket.optJSONObject("printer")
        g.pair("الطابعة", pr?.optString("name") ?: "")
        g.pair("الموديل", pr?.optString("model") ?: "")
        g.pair("الخط", "Cairo")
        g.pair("الحالة", "نجحت الطباعة العربية")
        g.rule(heavy = true)
        g.text("تفاصيل الطلب", 22f, true, center = false, spacing = 6f)
        g.item("شاورما دجاج", "1")
        g.text("   بدون بصل", 17f, false, center = false, spacing = 6f)
        g.rule(heavy = true)
        g.band("الإجمالي: " + money(0.0, cur), 30f, 8f)
      } else {
        val o = ticket.getJSONObject("order")
        val num = if (o.isNull("number")) "" else o.get("number").toString()
        g.text("#$num", 52f, true, center = true, spacing = 2f)
        g.text(o.optString("type_label"), 22f, true, center = true, spacing = 8f)
        g.dashed()
        g.pair("وقت الطلب", o.optString("received_at"))
        if (!o.isNull("customer_name")) g.pair("اسم العميل", o.getString("customer_name"))
        if (!o.isNull("customer_phone")) g.pair("رقم الهاتف", o.getString("customer_phone"))
        val addr = o.optJSONArray("address_lines")
        if (addr != null && addr.length() > 0) {
          val lines = (0 until addr.length()).joinToString("\n") { addr.getString(it) }
          g.text("عنوان التوصيل", 17f, false, center = false, spacing = 2f)
          g.text(lines, 21f, true, center = false, spacing = 8f)
        }
        g.rule(heavy = true)
        g.text("تفاصيل الطلب", 22f, true, center = false, spacing = 6f)
        g.dashed()
        val items = o.optJSONArray("items")
        if (items != null) for (i in 0 until items.length()) {
          val it = items.getJSONObject(i)
          g.item(it.optString("name"), it.optInt("quantity").toString())
          val opts = it.optJSONArray("options")
          if (opts != null) for (j in 0 until opts.length()) {
            val op = opts.getJSONObject(j)
            g.text(op.optString("option_name") + ": " + op.optString("value_name"), 17f, false, center = false, spacing = 2f, x = 60f, width = 504f)
          }
          if (!it.isNull("notes") && it.optString("notes").isNotBlank()) {
            g.text("ملاحظة: " + it.getString("notes"), 17f, false, center = false, spacing = 2f, x = 60f, width = 504f)
          }
          g.y += 6
          g.dashed()
        }
        if (!o.isNull("notes") && o.optString("notes").isNotBlank()) {
          g.text("ملاحظات الطلب: " + o.getString("notes"), 21f, true, center = false, spacing = 8f)
        }
        g.rule(heavy = true)
        g.pair("المجموع الفرعي", money(o.optDouble("subtotal"), cur))
        if (o.optDouble("delivery_fee") != 0.0) g.pair("رسوم التوصيل", money(o.optDouble("delivery_fee"), cur))
        if (o.optDouble("discount") != 0.0) g.pair("الخصم", money(o.optDouble("discount"), cur))
        g.y += 4
        g.band("الإجمالي: " + money(o.optDouble("total"), cur), 30f, 10f)
        g.pair("طريقة الدفع", o.optString("payment_label"))
        g.rule(heavy = true)
        g.text("شكراً لاختياركم لانا سناك", 21f, true, center = true, spacing = 4f)
        g.text("نتمنى لكم تجربة شهية", 17f, false, center = true, spacing = 6f)
      }
    }

    // تمريرة قياس لمعرفة الارتفاع الكلي
    blocks(g)
    val height = g.y.toInt() + 40

    val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.WHITE)
    val out = Ctx(canvas, reg, bold)
    out.y = 12f
    blocks(out)
    return bmp
  }

  /** تحويل الصورة إلى أوامر ESC/POS (GS v 0) مع قص الورق */
  fun toEscPos(bmp: Bitmap): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    out.write(byteArrayOf(0x1B, 0x40))
    val bytesPerRow = bmp.width / 8
    val px = IntArray(bmp.width)
    var y0 = 0
    while (y0 < bmp.height) {
      val h = minOf(200, bmp.height - y0)
      out.write(byteArrayOf(0x1D, 0x76, 0x30, 0, (bytesPerRow and 0xFF).toByte(), (bytesPerRow shr 8).toByte(), (h and 0xFF).toByte(), (h shr 8).toByte()))
      for (y in y0 until y0 + h) {
        bmp.getPixels(px, 0, bmp.width, 0, y, bmp.width, 1)
        for (bx in 0 until bytesPerRow) {
          var v = 0
          for (bit in 0 until 8) {
            val p = px[bx * 8 + bit]
            val lum = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
            if (lum < 140) v = v or (0x80 shr bit)
          }
          out.write(v)
        }
      }
      y0 += h
    }
    out.write(byteArrayOf(0x1B, 0x64, 4, 0x1D, 0x56, 66, 0))
    return out.toByteArray()
  }
}
