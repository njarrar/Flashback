package com.njarrar.backthen.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.njarrar.backthen.AppViewModel
import com.njarrar.backthen.ToastKind
import com.njarrar.backthen.Viewer
import com.njarrar.backthen.core.City
import com.njarrar.backthen.core.L
import com.njarrar.backthen.core.Photo
import com.njarrar.backthen.core.PlaceCard
import com.njarrar.backthen.core.Trip
import com.njarrar.backthen.core.agoLabel
import com.njarrar.backthen.core.formatDate
import com.njarrar.backthen.core.trips
import kotlinx.coroutines.CancellationException
import java.util.Locale

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

/** Localized country display name from an ISO-3166 alpha-2 code (e.g. "FR" -> "France" / "فرنسا"). */
internal fun countryName(code: String): String {
    if (code.isBlank()) return ""
    return runCatching { Locale("", code).getDisplayCountry(L.locale) }.getOrDefault("")
}

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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(p.fill), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Place, null, tint = p.muted2, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                T(L.s("place_been_before", "city" to card.city.name), size = 17, weight = FontWeight.ExtraBold)
                T(tripsRange(card.trips), size = 14, muted = true)
            }
            IconButton(onClick = vm::dismissPlaceCard, modifier = Modifier.padding(start = 4.dp).size(48.dp)) {
                Icon(Icons.Filled.Close, L.s("close"), tint = p.muted, modifier = Modifier.size(18.dp))
            }
        }
        if (photos.isNotEmpty()) {
            val openLabel = L.s("see_city", "city" to card.city.name)
            if (photos.size == 1) {
                val request = rememberThumbnailRequest(photos[0].uri, maxPx = 384)
                AsyncImage(
                    model = request,
                    contentDescription = openLabel,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .height(96.dp)
                        .aspectRatio(1.5f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(p.fill)
                        .clickable(role = Role.Button, onClick = onOpen),
                )
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = onOpen)
                        .semantics { contentDescription = openLabel },
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    photos.forEach { ph ->
                        val request = rememberThumbnailRequest(ph.uri, maxPx = 256)
                        AsyncImage(
                            model = request,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).height(68.dp).clip(RoundedCornerShape(10.dp)).background(p.fill),
                        )
                    }
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
    val context = LocalContext.current
    val reduce = LocalReduceMotion.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val state by vm.saved.collectAsStateWithLifecycle()
    val favorites = remember(state.favorites) { state.favorites.map { it.key }.toSet() }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var shareFor by remember { mutableStateOf<ShareRequest?>(null) }

    PredictiveBackHandler { progress ->
        try {
            progress.collect { event -> backProgress = if (reduce) 0f else event.progress }
            onBack()
        } catch (_: CancellationException) {
            backProgress = 0f
        }
    }

    val all = remember(placeIndex, city.id) { trips(placeIndex[city.id].orEmpty()) }
    val country = remember(city.country) { countryName(city.country) }
    val subtitle = remember(country, all) {
        val summary = tripsAndPhotos(all)
        if (country.isNotBlank()) "$country · $summary" else summary
    }

    // Root pointer blocker ensures touches never fall through to TodayScreen underneath.
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (backProgress > 0f) {
                    val dir = if (rtl) -1f else 1f
                    translationX = size.width * 0.25f * backProgress * dir
                    alpha = 1f - 0.35f * backProgress
                }
            }
            .background(p.bg)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp).size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, L.s("close"), tint = p.ink)
            }
            LazyColumn(contentPadding = PaddingValues(bottom = bottomPadding + 24.dp), modifier = Modifier.fillMaxSize()) {
                item(key = "place-header") {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        T(city.name, size = 32, weight = FontWeight.Bold, tight = true, modifier = Modifier.semantics { heading() })
                        T(subtitle, size = 15, muted = true)
                    }
                }
                items(all, key = { it.start.toString() }) { trip ->
                    TripSection(
                        vm = vm,
                        city = city,
                        trip = trip,
                        favorites = favorites,
                        onShare = { title, photos -> shareFor = ShareRequest(title, photos) },
                    )
                }
            }
        }
    }

    shareFor?.let { req ->
        ShareSheet(
            req,
            onSend = {
                if (!sharePhotos(context, req.photos, req.title)) {
                    vm.showToast(L.s("share_failed"), ToastKind.Error)
                }
                shareFor = null
            },
            onSave = { vm.saveToGallery(req.title, req.photos); shareFor = null },
            onDismiss = { shareFor = null },
        )
    }
}

/**
 * One visit: its month, how long ago and how long, share/reel actions, then up to seven photos in a mosaic.
 * The last tile says how many more there are; any tile opens the viewer on the whole visit.
 */
@Composable
private fun TripSection(
    vm: AppViewModel,
    city: City,
    trip: Trip,
    favorites: Set<String>,
    onShare: (String, List<Photo>) -> Unit,
) {
    val p = LocalPalette.current
    var photos by remember(city.id, trip) { mutableStateOf<List<Photo>?>(null) }
    LaunchedEffect(city.id, trip) { photos = vm.tripPhotos(city.id, trip) }
    val monthLabel = formatDate(trip.start, "yMMMM")
    val ago = agoLabel(trip.start.year)
    val subText = L.s(
        "ago_weekday",
        "ago" to ago,
        "weekday" to L.s("days_photos", "days" to L.p("days", trip.days), "photos" to L.p("photos", trip.photos)),
    )
    val all = photos
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                T(monthLabel, size = 30, weight = FontWeight.Black, tight = true, modifier = Modifier.semantics { heading() })
                Text(
                    styledAgoSubtitle(subText, ago),
                    color = p.muted,
                    fontFamily = Nunito,
                    fontSize = 14.sp,
                    lineHeight = (if (L.lang.arabicScript) 22 else 20).sp,
                )
            }
            if (!all.isNullOrEmpty()) {
                if (all.size >= 2) {
                    IconButton(onClick = { vm.startReel(all) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Movie, L.s("make_reel"), tint = p.ink, modifier = Modifier.size(22.dp))
                    }
                }
                IconButton(onClick = { onShare("${city.name} · $monthLabel", all) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Share, L.s("share"), tint = p.ink, modifier = Modifier.size(22.dp))
                }
            }
        }
        when {
            all == null -> Box(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp)
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .shimmer(p.fill, p.fill2)
                    .semantics { contentDescription = L.s("loading") },
            )
            all.isEmpty() -> Spacer(Modifier.height(28.dp))
            else -> {
                val shown = all.take(7)
                YearMosaic(shown, favorites, null, onOpen = { i -> vm.viewer.value = Viewer(all, i) }, more = all.size - shown.size)
            }
        }
    }
}

/** Asked once on Today: what places does, and that the location stays on the phone. */
@Composable
fun PlaceAskSheet(onTurnOn: () -> Unit, onNotNow: () -> Unit) {
    val p = LocalPalette.current
    BottomSheet(onNotNow) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
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
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Pill(L.s("turn_on"), onTurnOn, modifier = Modifier.fillMaxWidth())
                Pill(L.s("not_now"), onNotNow, kind = Kind.Ghost, height = 48.dp, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AskPoint(icon: ImageVector, text: String) {
    val p = LocalPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = p.muted2, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        T(text, size = 15, color = p.muted2)
    }
}
