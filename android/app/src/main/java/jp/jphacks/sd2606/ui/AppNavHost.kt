package jp.jphacks.sd2606.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import jp.jphacks.sd2606.data.Answer
import jp.jphacks.sd2606.ui.board.BoardViewModel
import jp.jphacks.sd2606.ui.board.ComposeScreen
import jp.jphacks.sd2606.ui.board.PostDetailScreen
import jp.jphacks.sd2606.ui.board.PostListScreen
import jp.jphacks.sd2606.ui.board.ProcedureReviews
import jp.jphacks.sd2606.ui.flow.FlowScreen
import jp.jphacks.sd2606.ui.profile.ProfileScreen
import jp.jphacks.sd2606.ui.recommend.RecommendScreen

/**
 * 下のタブ（手続き / 体験談）ごとに画面の履歴を持つ。タブを切り替えても、元のタブは開いていた画面に戻る。
 * 体験談の詳細・投稿は、どちらのタブからも開ける（開いたタブの中で進む）。
 */
private object Routes {
    const val TAB_PROCEDURES = "tab/procedures"
    const val TAB_BOARD = "tab/board"

    const val PROFILE = "profile"
    const val RECOMMEND = "recommend"
    const val FLOW = "flow/{id}"
    fun flow(id: String) = "flow/$id"
    const val FLOW_POSTS = "flow/{id}/posts"
    fun flowPosts(id: String) = "flow/$id/posts"

    const val BOARD = "board"

    /** 体験談の詳細・投稿の route。タブごとに別の route にして、開いたタブの履歴に積む */
    fun post(tab: String) = "$tab/post/{postId}"
    fun post(tab: String, id: Int) = "$tab/post/$id"
    fun compose(tab: String) = "$tab/compose?procedure={procedure}"
    fun compose(tab: String, procedure: String?) = "$tab/compose" + (procedure?.let { "?procedure=$it" } ?: "")
}

private data class Tab(val route: String, val label: String, val selected: ImageVector, val unselected: ImageVector)

private val TABS = listOf(
    Tab(Routes.TAB_PROCEDURES, "手続き", Icons.Filled.Checklist, Icons.Outlined.Checklist),
    Tab(Routes.TAB_BOARD, "体験談", Icons.Filled.Forum, Icons.Outlined.Forum),
)

private const val SLIDE_MS = 300
private const val FADE_MS = 200

/** タブをまたぐ遷移か（同じタブの中なら横スライド、タブの切り替えはフェード）。 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch() =
    initialState.destination.parent?.route != targetState.destination.parent?.route

@Composable
fun AppNavHost(vm: AppViewModel = viewModel(), boardVm: BoardViewModel = viewModel()) {
    val nav = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()
    val board by boardVm.state.collectAsStateWithLifecycle()
    val current by nav.currentBackStackEntryAsState()
    // 投稿の画面ではキーボードの邪魔になるので下のタブを隠す
    val showBar = current?.destination?.route?.contains("/compose") != true

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    TABS.forEach { tab ->
                        val selected = current?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(if (selected) tab.selected else tab.unselected, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        // 画面ごとの Scaffold が下の余白（タブの高さ）を二重に取らないよう、ここで消費する
        NavHost(
            navController = nav,
            startDestination = Routes.TAB_PROCEDURES,
            modifier = Modifier.padding(inner).consumeWindowInsets(inner),
            enterTransition = { if (isTabSwitch()) fadeIn(tween(FADE_MS)) else slideIntoContainer(SlideDirection.Start, tween(SLIDE_MS)) },
            exitTransition = { if (isTabSwitch()) fadeOut(tween(FADE_MS)) else slideOutOfContainer(SlideDirection.Start, tween(SLIDE_MS)) },
            popEnterTransition = { if (isTabSwitch()) fadeIn(tween(FADE_MS)) else slideIntoContainer(SlideDirection.End, tween(SLIDE_MS)) },
            popExitTransition = { if (isTabSwitch()) fadeOut(tween(FADE_MS)) else slideOutOfContainer(SlideDirection.End, tween(SLIDE_MS)) },
        ) {
            navigation(startDestination = Routes.PROFILE, route = Routes.TAB_PROCEDURES) {
                composable(Routes.PROFILE) {
                    ProfileScreen(
                        catalog = state.catalog,
                        answers = state.answers,
                        onAnswer = vm::answer,
                        onRetry = vm::loadCatalog,
                        onSubmit = {
                            vm.submitInitial()
                            nav.navigate(Routes.RECOMMEND)
                        },
                    )
                }
                composable(Routes.RECOMMEND) {
                    RecommendScreen(
                        state = state,
                        onAnswer = vm::answer,
                        onClearAnswers = {
                            vm.clearAnswers()
                            nav.popBackStack(Routes.PROFILE, inclusive = false)
                        },
                        onRetry = vm::loadCatalog,
                        onBack = { nav.popBackStack() },
                        onOpen = { id -> nav.navigate(Routes.flow(id)) },
                        reviewCounts = board.counts,
                    )
                }
                composable(Routes.FLOW) { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    FlowScreen(
                        state = state,
                        procedureId = id,
                        onAnswer = vm::answer,
                        onRetry = vm::loadCatalog,
                        onBack = { nav.popBackStack() },
                    ) {
                        ProcedureReviews(
                            vm = boardVm,
                            procedureId = id,
                            onOpen = { postId -> nav.navigate(Routes.post(Routes.TAB_PROCEDURES, postId)) },
                            onMore = { nav.navigate(Routes.flowPosts(id)) },
                            onWrite = { nav.navigate(Routes.compose(Routes.TAB_PROCEDURES, id)) },
                        )
                    }
                }
                composable(Routes.FLOW_POSTS) { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    val title = (state.catalog as? UiResult.Success)?.data?.procedures?.firstOrNull { it.id == id }?.title
                    PostListScreen(
                        vm = boardVm,
                        title = title?.let { "「$it」の体験談" } ?: "体験談",
                        procedureId = id,
                        onBack = { nav.popBackStack() },
                        onOpen = { postId -> nav.navigate(Routes.post(Routes.TAB_PROCEDURES, postId)) },
                        onWrite = { nav.navigate(Routes.compose(Routes.TAB_PROCEDURES, id)) },
                    )
                }
                postScreens(Routes.TAB_PROCEDURES, nav, boardVm) { state.answers }
            }

            navigation(startDestination = Routes.BOARD, route = Routes.TAB_BOARD) {
                composable(Routes.BOARD) {
                    PostListScreen(
                        vm = boardVm,
                        title = "みんなの体験談",
                        onOpen = { postId -> nav.navigate(Routes.post(Routes.TAB_BOARD, postId)) },
                        onWrite = { nav.navigate(Routes.compose(Routes.TAB_BOARD, null)) },
                    )
                }
                postScreens(Routes.TAB_BOARD, nav, boardVm) { state.answers }
            }
        }
    }
}

/** 体験談の詳細と投稿（タブごとに登録する）。投稿したら、その投稿の詳細に置き換える。 */
private fun NavGraphBuilder.postScreens(tab: String, nav: NavHostController, boardVm: BoardViewModel, answers: () -> Map<String, Answer>) {
    composable(Routes.post(tab), arguments = listOf(navArgument("postId") { type = NavType.IntType })) { entry ->
        PostDetailScreen(vm = boardVm, postId = entry.arguments?.getInt("postId") ?: 0, onBack = { nav.popBackStack() })
    }
    composable(
        Routes.compose(tab),
        arguments = listOf(navArgument("procedure") { type = NavType.StringType; nullable = true; defaultValue = null }),
    ) { entry ->
        ComposeScreen(
            vm = boardVm,
            answers = answers(),
            procedureId = entry.arguments?.getString("procedure"),
            onBack = { nav.popBackStack() },
            onPosted = { post ->
                nav.popBackStack()
                nav.navigate(Routes.post(tab, post.id))
            },
        )
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
