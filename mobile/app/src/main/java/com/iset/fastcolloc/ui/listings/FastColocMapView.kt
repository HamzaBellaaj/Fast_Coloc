package com.iset.fastcolloc.ui.listings

import android.content.Context
import android.util.AttributeSet
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay

/** Native map, app-private tile cache, identified OSM requests; no API key or storage permission. */
class FastColocMapView(context: Context, attrs: AttributeSet? = null) : MapView(configure(context), attrs) {
    init {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        controller.setZoom(6.0)
        controller.setCenter(GeoPoint(34.1, 9.6))
        overlays.add(CopyrightOverlay(context).apply { setCopyrightNotice("© OpenStreetMap contributors") })
        minZoomLevel = 5.0
        maxZoomLevel = 19.0
    }
    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(event.actionMasked != android.view.MotionEvent.ACTION_UP && event.actionMasked != android.view.MotionEvent.ACTION_CANCEL)
        return super.dispatchTouchEvent(event)
    }
    companion object {
        private fun configure(context: Context): Context {
            Configuration.getInstance().apply {
                userAgentValue = "FastColoc/0.1 (${context.packageName})"
                osmdroidBasePath = java.io.File(context.cacheDir, "maps").apply { mkdirs() }
                osmdroidTileCache = java.io.File(osmdroidBasePath, "tiles").apply { mkdirs() }
                tileDownloadThreads = 2
            }
            return context
        }
    }
}
