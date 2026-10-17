package jp.jphacks.sd2606.ui

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import jp.jphacks.sd2606.ui.flow.FlowScreen
import jp.jphacks.sd2606.ui.profile.ProfileScreen
import jp.jphacks.sd2606.ui.recommend.RecommendScreen

private object Routes {
    const val PROFILE = "profile"
    const val RECOMMEND = "recommend"
    const val FLOW = "flow/{id}"
    fun flow(id: String) = "flow/$id"
}

private const val SLIDE_MS = 300

@Composable
fun AppNavHost(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()

    // 画面遷移は横スライド: 進むときは右から入って左へ抜け、戻るときはその逆
    NavHost(
        navController = nav,
        startDestination = Routes.PROFILE,
        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(SLIDE_MS)) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start, tween(SLIDE_MS)) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End, tween(SLIDE_MS)) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(SLIDE_MS)) },
    ) {
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
                onComplete = vm::setCompleted,
                onClearAll = {
                    vm.clearAll()
                    nav.popBackStack(Routes.PROFILE, inclusive = false)
                },
                onClearCompleted = vm::clearCompleted,
                onClearDismissed = vm::clearDismissed,
                onRetry = vm::loadCatalog,
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate(Routes.flow(id)) },
            )
        }
        composable(Routes.FLOW) { entry ->
            FlowScreen(
                state = state,
                procedureId = entry.arguments?.getString("id").orEmpty(),
                onAnswer = vm::answer,
                onComplete = vm::setCompleted,
                onDismiss = vm::setDismissed,
                onRetry = vm::loadCatalog,
                onBack = { nav.popBackStack() },
            )
        }
    }
}
