package com.iset.fastcolloc.ui.listings

import android.os.Handler
import android.os.Looper
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.button.MaterialButton
import com.iset.fastcolloc.databinding.IncludeListingLocationBinding
import com.iset.fastcolloc.viewmodel.LocationViewModel
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.*

/** Binds only the existing address/coordinate fields; other form values and photos are untouched. */
class EditorLocationController(
    owner: LifecycleOwner, private val b: IncludeListingLocationBinding,
    private val street: EditText, private val latitude: EditText, private val longitude: EditText,
    private val model: LocationViewModel, private val busy: (Boolean) -> Unit,
    private val changed: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var syncing = false
    private var revision = model.state.value?.revision ?: 0
    private var marker: Marker? = null
    private val validate = Runnable { model.validate(number(latitude), number(longitude)) }
    private val watchers = mutableListOf<android.text.TextWatcher>()
    init {
        initial()
        b.editorMap.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                handler.removeCallbacks(validate)
                model.validate(point.latitude, point.longitude)
                return true
            }
            override fun longPressHelper(point: GeoPoint) = false
        }))
        for (field in listOf(latitude, longitude)) watchers += field.doAfterTextChanged {
            if (!syncing) {
                handler.removeCallbacks(validate)
                busy(true)
                handler.postDelayed(validate, 650)
            }
        }
        b.btnCoordinates.setOnClickListener { handler.removeCallbacks(validate); validate.run() }
        b.btnSearchAddress.setOnClickListener { handler.removeCallbacks(validate); model.search(street.text.toString()) }
        model.state.observe(owner) { state ->
            busy(state.loading)
            b.geoProgress.isVisible = state.loading
            b.btnSearchAddress.isEnabled = !state.loading
            b.btnCoordinates.isEnabled = !state.loading
            b.tvLocationState.text = if (state.loading) "Vérification de la position terrestre…" else state.message
            b.results.removeAllViews()
            for (place in state.results) b.results.addView(MaterialButton(b.root.context).apply {
                text = place.address
                isEnabled = !state.loading
                setOnClickListener { model.validate(place.latitude, place.longitude) }
            })
            if (revision != state.revision) {
                revision = state.revision
                syncing = true
                val point = state.position
                latitude.setText(point?.latitude?.toString().orEmpty())
                longitude.setText(point?.longitude?.toString().orEmpty())
                if (!state.restore) street.setText(point?.address.orEmpty())
                syncing = false
                changed()
                draw(true)
            } else draw(false)
        }
    }
    fun initial() {
        handler.removeCallbacks(validate)
        model.initial(number(latitude), number(longitude), street.text.toString())
        draw(true)
    }
    private fun draw(center: Boolean) {
        marker?.let { b.editorMap.overlays.remove(it) }; marker = null
        val point = model.state.value?.position ?: run { b.editorMap.invalidate(); return }
        val location = GeoPoint(point.latitude, point.longitude)
        marker = Marker(b.editorMap).apply {
            position = location
            title = "Position de l’annonce"
            isDraggable = true
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDragStart(marker: Marker) = Unit
                override fun onMarkerDrag(marker: Marker) = Unit
                override fun onMarkerDragEnd(marker: Marker) {
                    val candidate = marker.position
                    draw(false)
                    model.validate(candidate.latitude, candidate.longitude)
                }
            })
        }.also { b.editorMap.overlays.add(it) }
        if (center) { b.editorMap.controller.setCenter(location); b.editorMap.controller.setZoom(15.0) }
        b.editorMap.invalidate()
    }
    fun destroy() {
        handler.removeCallbacks(validate)
        listOf(latitude, longitude).zip(watchers).forEach { (field, watcher) -> field.removeTextChangedListener(watcher) }
        b.editorMap.onDetach()
    }
    private fun number(field: EditText) = field.text.toString().replace(',', '.').toDoubleOrNull()
}
