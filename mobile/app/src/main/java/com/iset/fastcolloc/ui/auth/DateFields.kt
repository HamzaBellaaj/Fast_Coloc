package com.iset.fastcolloc.ui.auth

import android.graphics.Rect
import android.text.method.TransformationMethod
import android.view.View
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.CompositeDateValidator
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** ISO stays in the DTO; the read-only control displays a French date. */
fun Fragment.calendarField(field: EditText, title: String, optional: Boolean = false, minimum: () -> String? = { null }, maximum: () -> String? = { null }) {
    fun iso() = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }
    fun millis(value: String?) = value?.let { runCatching { iso().parse(it)?.time }.getOrNull() }
    field.isFocusable = false
    field.isCursorVisible = false
    field.hint = "Choisir une date"
    field.transformationMethod = object : TransformationMethod {
        override fun getTransformation(source: CharSequence?, view: View?): CharSequence = millis(source?.toString())?.let { SimpleDateFormat("dd MMMM yyyy", Locale.FRANCE).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(java.util.Date(it)) } ?: (source ?: "")
        override fun onFocusChanged(view: View?, source: CharSequence?, focused: Boolean, direction: Int, rect: Rect?) = Unit
    }
    val container = field.parent.parent as? TextInputLayout
    if (optional) {
        container?.endIconMode = TextInputLayout.END_ICON_CUSTOM
        container?.setEndIconDrawable(android.R.drawable.ic_menu_close_clear_cancel)
        container?.endIconContentDescription = "Effacer la date"
        container?.setEndIconOnClickListener { field.setText("") }
    }
    field.setOnClickListener {
        val tag = "calendar-" + field.id
        if (childFragmentManager.findFragmentByTag(tag) != null) return@setOnClickListener
        val low = millis(minimum()); val high = millis(maximum())
        val validators = mutableListOf<CalendarConstraints.DateValidator>()
        low?.let { validators.add(DateValidatorPointForward.from(it)) }
        high?.let { validators.add(DateValidatorPointBackward.before(it)) }
        val constraints = CalendarConstraints.Builder().setValidator(CompositeDateValidator.allOf(validators)).build()
        val builder = MaterialDatePicker.Builder.datePicker().setTitleText(title).setPositiveButtonText("Confirmer").setNegativeButtonText("Annuler").setCalendarConstraints(constraints)
        millis(field.text.toString())?.takeIf { (low == null || it >= low) && (high == null || it <= high) }?.let { builder.setSelection(it) }
        builder.build().apply {
            addOnPositiveButtonClickListener { selection -> field.setText(iso().format(java.util.Date(selection))); field.error = null }
        }.show(childFragmentManager, tag)
    }
}
