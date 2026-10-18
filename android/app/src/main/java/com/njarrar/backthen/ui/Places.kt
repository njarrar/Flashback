package com.njarrar.backthen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.njarrar.backthen.AppViewModel
import com.njarrar.backthen.Viewer
import com.njarrar.backthen.core.City
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.PlaceCard
import com.njarrar.backthen.core.Trip
import com.njarrar.backthen.core.agoLabel
import com.njarrar.backthen.core.formatDate
import com.njarrar.backthen.core.trips

/** "3 trips, from May 2019 to June 2023. 214 photos." Trips come newest first. */
fun tripsRange(trips: List<Trip>): String {
    if (trips.isEmpty()) return ""
    val first = formatDate(trips.last().start, "yMMMM")
    val last = formatDate(trips.first().start, "yMMMM")
    return L.s(
        if (first == last) "place_card_one" else "place_card_range",
        "trips" to L.p("trips", trips.size), "photos" to L.p("photos", trips.sumOf { it.photos }), "first" to first, "last" to last,
    )
}

/** "3 trips · 214 photos". */
fun tripsAndPhotos(trips: List<Trip>): String =
    L.s("trips_photos", "trips" to L.p("trips", trips.size), "photos" to L.p("photos", trips.sumOf { it.photos }))

/**
 * On Today, in a city you've been to before: how many trips and photos, a few of them,
 * and a button to the city page.
 */
@Composable
fun PlaceCardView(vm: AppViewModel, card: PlaceCard, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    var photos by remember(card.city.id) { mutableStateOf<List<Photo>>(emptyList()) }
    LaunchedEffect(card.city.id) {
        card.trips.firstOrNull()?.let { photos = vm.tripPhotos(card.city.id, it).take(4) }
    }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.surface)
            .border(1.dp, p.line, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(p.fill), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Place, null, tint = p.muted2, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(L.s("place_been_before", "city" to card.city.name), size = 17, weight = FontWeight.ExtraBold)
                T(tripsRange(card.trips), size = 14, muted = true)
            }
            IconButton(onClick = vm::dismissPlaceCard, modifier = Modifier.size(48.dp).padding(start = 8.dp)) {
                Icon(Icons.Filled.Close, L.s("close"), tint = p.muted, modifier = Modifier.size(18.dp))
            }
        }
        if (photos.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                photos.forEach { ph ->
                    AsyncImage(
                        model = ph.uri, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(10.dp)).background(p.fill),
                    )
                }
            }
        }
        Pill(L.s("see_city", "city" to card.city.name), onOpen, kind = Kind.Secondary, height = 44.dp, modifier = Modifier.fillMaxWidth())
    }
}

/** Every visit to a city, newest first, each with a few of its photos. Covers Today until closed. */
@Composable
fun PlaceScreen(vm: AppViewModel, city: City, placeIndex: Map<Int, Map<String, Int>>, bottomPadding: Dp, onBack: () -> Unit) {
    val p = LocalPalette.current
    BackHandler(onBack = onBack)
    val all = remember(placeIndex, city.id) { trips(placeIndex[city.id].orEmpty()) }
    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, L.s("close"), tint = p.ink)
        }
        LazyColumn(contentPadding = PaddingValues(bottom = bottomPadding + 24.dp), modifier = Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    T(city.name, size = 32, weight = FontWeight.Bold, tight = true, modifier = Modifier.semantics { heading() })
                    T(tripsAndPhotos(all), size = 15, muted = true)
                }
            }
            items(all, key = { it.start.toString() }) { trip -> TripSection(vm, city.id, trip) }
        }
    }
}

/**
 * One visit: its month, how long ago and how long, then up to seven photos in a mosaic.
 * The last tile says how many more there are; any tile opens the viewer on the whole visit.
 */
@Composable
private fun TripSection(vm: AppViewModel, city: Int, trip: Trip) {
    val p = LocalPalette.current
    var photos by remember(city, trip) { mutableStateOf<List<Photo>?>(null) }
    LaunchedEffect(city, trip) { photos = vm.tripPhotos(city, trip) }
    Column {
        Column(Modifier.padding(horizontal = 20.dp)) {
            T(formatDate(trip.start, "yMMMM"), size = 30, weight = FontWeight.Black, tight = true, modifier = Modifier.semantics { heading() })
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = p.ink, fontWeight = FontWeight.Bold)) { append(agoLabel(trip.start.year)) }
                    append(" · " + L.s("days_photos", "days" to L.p("days", trip.days), "photos" to L.p("photos", trip.photos)))
                },
                color = p.muted, fontFamily = Nunito, fontSize = 14.sp, lineHeight = (if (L.lang.arabicScript) 22 else 20).sp,
            )
        }
        val all = photos
        when {
            all == null -> Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp).fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)).background(p.fill))
            all.isEmpty() -> Spacer(Modifier.height(28.dp))
            else -> {
                val shown = all.take(7)
                YearMosaic(shown, emptySet(), null, onOpen = { i -> vm.viewer.value = Viewer(all, i) }, more = all.size - shown.size)
            }
        }
    }
}

/** Asked once on Today: what places does, and that the location stays on the phone. */
@Composable
fun PlaceAskSheet(onTurnOn: () -> Unit, onNotNow: () -> Unit) {
    val p = LocalPalette.current
    BottomSheet(onNotNow) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(p.fill), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Place, null, tint = p.muted2, modifier = Modifier.size(28.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T(L.s("places_ask_title"), size = 26, weight = FontWeight.ExtraBold)
            T(L.s("places_ask_body"), size = 16, color = p.muted2)
        }
        AskPoint(Icons.Outlined.Lock, L.s("places_ask_private"))
        AskPoint(Icons.Outlined.Schedule, L.s("places_ask_open_only"))
        AskPoint(Icons.Outlined.Settings, L.s("places_ask_settings"))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Pill(L.s("turn_on"), onTurnOn, modifier = Modifier.fillMaxWidth())
            Pill(L.s("not_now"), onNotNow, kind = Kind.Ghost, height = 48.dp, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AskPoint(icon: ImageVector, text: String) {
    val p = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = p.muted2, modifier = Modifier.size(20.dp).width(22.dp))
        T(text, size = 15, color = p.muted2)
    }
}
