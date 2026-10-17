package jp.jphacks.sd2606.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@Composable
fun AppNavHost(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()

    NavHost(navController = nav, startDestination = Routes.PROFILE) {
        composable(Routes.PROFILE) {
            ProfileScreen(
                profile = state.profile,
                onChange = vm::updateProfile,
                onSubmit = {
                    vm.loadRecommendations()
                    nav.navigate(Routes.RECOMMEND)
                },
            )
        }
        composable(Routes.RECOMMEND) {
            RecommendScreen(
                result = state.recommendations,
                onRetry = vm::loadRecommendations,
                onBack = { nav.popBackStack() },
                onOpen = { id -> nav.navigate(Routes.flow(id)) },
            )
        }
        composable(Routes.FLOW) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            LaunchedEffect(id) { vm.loadProcedure(id) }
            FlowScreen(
                result = state.procedure,
                onRetry = { vm.loadProcedure(id) },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
