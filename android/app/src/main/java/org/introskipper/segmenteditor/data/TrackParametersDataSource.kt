package org.introskipper.segmenteditor.data

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource

/**
 * Factory for creating ResolvingDataSource that dynamically adds audio and subtitle track
 * parameters to the stream URL at request time, avoiding the need to reload media when tracks change.
 */
@UnstableApi
class TrackParametersDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val getAudioStreamIndex: () -> Int?,
    private val getSubtitleStreamIndex: () -> Int?
) : DataSource.Factory {
    
    // Create resolver once and reuse it for all data sources
    private val resolver = object : ResolvingDataSource.Resolver {
        override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
            val originalUri = dataSpec.uri
            
            // Build new URI with dynamic parameters
            val uriBuilder = originalUri.buildUpon()
            
            // Add audio stream index if selected
            // Note: Lambda is called here at request time, ensuring we get current value
            val audioIndex = getAudioStreamIndex()
            if (audioIndex != null) {
                uriBuilder.appendQueryParameter("AudioStreamIndex", audioIndex.toString())
            }
            
            // Add subtitle stream index if selected
            val subtitleIndex = getSubtitleStreamIndex()
            if (subtitleIndex != null) {
                uriBuilder.appendQueryParameter("SubtitleStreamIndex", subtitleIndex.toString())
            }
            
            val resolvedUri = uriBuilder.build()
            
            // Log the resolution for debugging
            if (originalUri != resolvedUri) {
                android.util.Log.d(
                    "TrackParametersDataSource",
                    "Resolved URI: $originalUri -> $resolvedUri"
                )
            }
            
            // Return new DataSpec with resolved URI
            return dataSpec.buildUpon().setUri(resolvedUri).build()
        }

        override fun resolveReportedUri(uri: Uri): Uri {
            // Return the original URI for reporting purposes
            // Strip the dynamic parameters we added
            val builder = uri.buildUpon()
            builder.clearQuery()
            
            // Re-add all query parameters except the track-specific ones
            for (paramName in uri.queryParameterNames) {
                if (paramName != "AudioStreamIndex" && paramName != "SubtitleStreamIndex") {
                    val values = uri.getQueryParameters(paramName)
                    for (value in values) {
                        builder.appendQueryParameter(paramName, value)
                    }
                }
            }
            
            return builder.build()
        }
    }
    
    override fun createDataSource(): DataSource {
        return ResolvingDataSource(upstreamFactory.createDataSource(), resolver)
    }
}
