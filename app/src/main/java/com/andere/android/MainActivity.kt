package com.andere.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.andere.android.data.local.TagSuggestionService
import com.andere.android.data.local.TagTranslationRepository
import com.andere.android.ui.browse.BrowseScreen
import com.andere.android.ui.browse.BrowseViewModel
import com.andere.android.ui.detail.DetailScreen
import com.andere.android.ui.detail.DetailViewModel
import com.andere.android.ui.favorites.FavoritesScreen
import com.andere.android.ui.favorites.FavoritesViewModel
import com.andere.android.ui.settings.SettingsScreen
import com.andere.android.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as AndereApplication).appContainer }
    private val browseViewModel by viewModels<BrowseViewModel> {
        BrowseViewModel.factory(
            searchPostsUseCase = container.searchPostsUseCase,
            postRepository = container.postRepository,
            searchHistoryDao = container.searchHistoryDao,
            preferencesRepository = container.preferencesRepository,
            wallpaperRefreshService = container.wallpaperRefreshService,
        )
    }
    private val settingsViewModel by viewModels<SettingsViewModel> {
        SettingsViewModel.factory(
            preferencesRepository = container.preferencesRepository,
            wallpaperScheduler = container.wallpaperScheduler,
            wallpaperRefreshService = container.wallpaperRefreshService,
            wallpaperRecordDao = container.wallpaperRecordDao,
            tagSyncService = container.tagSyncService,
            onTagSyncScheduleChanged = { enabled ->
                (application as AndereApplication).syncTagSchedule(enabled)
            },
        )
    }
    private val detailViewModel by viewModels<DetailViewModel> {
        DetailViewModel.factory(
            favoritePostDao = container.favoritePostDao,
            preferencesRepository = container.preferencesRepository,
            imageSaveService = container.imageSaveService,
            tagTranslationRepository = container.tagTranslationRepository,
            wallpaperScheduler = container.wallpaperScheduler,
        )
    }
    private val favoritesViewModel by viewModels<FavoritesViewModel> {
        FavoritesViewModel.factory(
            favoritePostDao = container.favoritePostDao,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                AndereApp(
                    browseViewModel = browseViewModel,
                    settingsViewModel = settingsViewModel,
                    detailViewModel = detailViewModel,
                    favoritesViewModel = favoritesViewModel,
                    tagTranslationRepository = container.tagTranslationRepository,
                    tagSuggestionService = container.tagSuggestionService,
                )
            }
        }
    }
}

@Composable
private fun AndereApp(
    browseViewModel: BrowseViewModel,
    settingsViewModel: SettingsViewModel,
    detailViewModel: DetailViewModel,
    favoritesViewModel: FavoritesViewModel,
    tagTranslationRepository: TagTranslationRepository,
    tagSuggestionService: TagSuggestionService,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    var detailInitialIndex by remember { mutableIntStateOf(0) }
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route?.substringBefore('/') ?: Destinations.Browse

    Scaffold(
        bottomBar = {
            if (currentRoute != Destinations.Detail) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Destinations.Browse,
                        onClick = {
                            navController.navigate(Destinations.Browse) {
                                popUpTo(Destinations.Browse) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        label = { Text("浏览") },
                        icon = { Icon(painterResource(R.drawable.ic_browse), contentDescription = "浏览") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Destinations.Favorites,
                        onClick = {
                            navController.navigate(Destinations.Favorites) {
                                popUpTo(Destinations.Browse) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        label = { Text("收藏") },
                        icon = { Icon(painterResource(R.drawable.ic_favorite), contentDescription = "收藏") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Destinations.Settings,
                        onClick = {
                            navController.navigate(Destinations.Settings) {
                                popUpTo(Destinations.Browse) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        label = { Text("设置") },
                        icon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = "设置") },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            modifier = Modifier.padding(innerPadding),
            navController = navController,
            startDestination = Destinations.Browse,
        ) {
            composable(Destinations.Browse) {
                BrowseScreen(
                    viewModel = browseViewModel,
                    tagSuggestionService = tagSuggestionService,
                    tagTranslationRepository = tagTranslationRepository,
                    onOpenDetail = { post ->
                        val posts = browseViewModel.uiState.value.posts
                        detailInitialIndex = posts.indexOfFirst { it.id == post.id }.coerceAtLeast(0)
                        navController.navigate(Destinations.Detail)
                    },
                )
            }
            composable(Destinations.Favorites) {
                FavoritesScreen(
                    viewModel = favoritesViewModel,
                    onOpenFavorite = { postId ->
                        coroutineScope.launch {
                            val loadedPost = browseViewModel.fetchPostById(postId)
                            if (loadedPost != null) {
                                browseViewModel.ensurePostInList(loadedPost)
                                val posts = browseViewModel.uiState.value.posts
                                detailInitialIndex = posts.indexOfFirst { it.id == postId }.coerceAtLeast(0)
                                navController.navigate(Destinations.Detail)
                            }
                        }
                    },
                )
            }
            composable(Destinations.Settings) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    tagSuggestionService = tagSuggestionService,
                    tagTranslationRepository = tagTranslationRepository,
                    onOpenRecord = { postId ->
                        coroutineScope.launch {
                            val loadedPost = browseViewModel.fetchPostById(postId)
                            if (loadedPost != null) {
                                browseViewModel.ensurePostInList(loadedPost)
                                val posts = browseViewModel.uiState.value.posts
                                detailInitialIndex = posts.indexOfFirst { it.id == postId }.coerceAtLeast(0)
                                navController.navigate(Destinations.Detail)
                            }
                        }
                    },
                )
            }
            composable(Destinations.Detail) {
                val browseState by browseViewModel.uiState.collectAsStateWithLifecycle()
                if (browseState.posts.isNotEmpty()) {
                    DetailScreen(
                        posts = browseState.posts,
                        initialIndex = detailInitialIndex,
                        viewModel = detailViewModel,
                        onBack = { navController.popBackStack() },
                        onApply = { post, target -> browseViewModel.applyPost(post, target) },
                        onOpenPostInBrowser = { post ->
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://yande.re/post/show/${post.id}")),
                            )
                        },
                        onOpenSourceInBrowser = { source ->
                            source?.takeIf { it.isNotBlank() }?.let { url ->
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        },
                        onTagClick = { tag ->
                            browseViewModel.searchWithQuery(tag)
                            navController.popBackStack(Destinations.Browse, false)
                        },
                        onNearEnd = { browseViewModel.loadNextPage() },
                    )
                }
            }
        }
    }
}

private object Destinations {
    const val Browse = "browse"
    const val Settings = "settings"
    const val Favorites = "favorites"
    const val Detail = "detail"
}
