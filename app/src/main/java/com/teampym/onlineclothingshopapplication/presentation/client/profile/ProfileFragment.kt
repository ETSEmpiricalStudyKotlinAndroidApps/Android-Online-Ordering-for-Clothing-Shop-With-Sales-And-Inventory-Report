package com.teampym.onlineclothingshopapplication.presentation.client.profile

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.firebase.ui.auth.AuthUI
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.teampym.onlineclothingshopapplication.R
import com.teampym.onlineclothingshopapplication.data.util.LoadingDialog
import com.teampym.onlineclothingshopapplication.data.util.UserType
import com.teampym.onlineclothingshopapplication.databinding.FragmentProfileBinding
import com.teampym.onlineclothingshopapplication.presentation.registration.ADD_EDIT_PROFILE_REQUEST
import com.teampym.onlineclothingshopapplication.presentation.registration.ADD_EDIT_PROFILE_RESULT
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest

private const val RC_SIGN_IN = 699

private const val TAG = "ProfileFragment"

@AndroidEntryPoint
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var binding: FragmentProfileBinding

    private val viewModel: ProfileViewModel by viewModels()

    private lateinit var loadingDialog: LoadingDialog

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentProfileBinding.bind(view)

        loadingDialog = LoadingDialog(requireActivity())

        setupViews()

        lifecycleScope.launchWhenStarted {
            viewModel.profileEvent.collectLatest { event ->
                when (event) {
                    is ProfileViewModel.ProfileEvent.VerificationSent -> {
                        Snackbar.make(
                            requireView(),
                            "Verification sent to your email. Please check it.",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    ProfileViewModel.ProfileEvent.NotRegistered -> {
                        val action =
                            ProfileFragmentDirections.actionProfileFragmentToRegistrationFragment(
                                false
                            )
                        findNavController().navigate(action)
                    }
                    ProfileViewModel.ProfileEvent.SignedOut -> {
                        resetInformation()
                    }
                    is ProfileViewModel.ProfileEvent.ShowSuccessMessage -> {
                        Snackbar.make(
                            requireView(),
                            event.msg,
                            Snackbar.LENGTH_LONG
                        ).show()

                        getFirebaseUser()?.let {
                            // Re-fetch the userInformation from local db
                            // Then update the View Automatically
                            // There is an observer above
                            viewModel.fetchUserFromLocalDb(it.uid)
                        }
                    }
                    is ProfileViewModel.ProfileEvent.ShowErrorMessage -> {
                        Snackbar.make(
                            requireView(),
                            event.msg,
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun setupViews() {
        binding.apply {

            tvAddress.setOnClickListener {
                findNavController().navigate(R.id.action_profileFragment_to_deliveryInformationFragment)
            }

            tvProfile.setOnClickListener {
                val action =
                    ProfileFragmentDirections.actionProfileFragmentToRegistrationFragment(editMode = true)
                findNavController().navigate(action)
            }

            setFragmentResultListener(ADD_EDIT_PROFILE_REQUEST) { _, bundle ->
                val result = bundle.getInt(ADD_EDIT_PROFILE_RESULT)
                Log.d(TAG, "setupViews: $result")
                viewModel.onAddEditProfileResult(result)
            }

            tvWishList.setOnClickListener {
                findNavController().navigate(R.id.action_profileFragment_to_wishListFragment)
            }

            tvHistoryLog.setOnClickListener {
                findNavController().navigate(R.id.action_profileFragment_to_historyLogFragment)
            }

            tvSales.setOnClickListener {
                findNavController().navigate(R.id.action_profileFragment_to_salesFragment)
            }

            tvToc.setOnClickListener {
                findNavController().navigate(R.id.action_profileFragment_to_termsAndConditionFragment)
            }

            tvSizeChart.setOnClickListener {
                findNavController().navigate(R.id.action_profileFragment_to_sizeChartFragment)
            }

            btnSendVerification.setOnClickListener {
                getFirebaseUser()?.sendEmailVerification()
                viewModel.sendVerificationAgain()
            }

            FirebaseAuth.getInstance().addAuthStateListener { auth ->
                if (auth.currentUser == null) {
                    btnSignInAndOut.text = getString(R.string.btn_sign_in)
                    btnSignInAndOut.setOnClickListener {
                        showSignInMethods()
                    }
                } else {
                    btnSignInAndOut.text = getString(R.string.btn_sign_out)
                    btnSignInAndOut.setOnClickListener {
                        showSignOutDialog(auth)
                    }
                }
            }
        }

        viewModel.userSession.observe(viewLifecycleOwner) { sessionPref ->
            if (sessionPref.userId.isNotBlank()) {
                Log.d(TAG, sessionPref.userId)
                loadingDialog.show()
                viewModel.fetchUserFromLocalDb(sessionPref.userId)
            }
        }

        viewModel.user.observe(viewLifecycleOwner) { userInformation ->
            userInformation?.let { user ->
                if (loadingDialog.isActive()) {
                    loadingDialog.dismiss()
                }

                // Check if the user is already registered in the remote db
                if (userInformation.firstName.isBlank()) {
                    viewModel.navigateUserToRegistrationModule()
                }

                binding.apply {

                    if (userInformation.userType == UserType.CUSTOMER.name) {
                        tvAddress.isVisible = true
                        tvProfile.isVisible = true
                        tvWishList.isVisible = true

                        // check if the user is already verified in email
                        val currentUser = getFirebaseUser()
                        if (currentUser != null) {
                            cardViewBanner.isVisible = !currentUser.isEmailVerified
                        }
                    } else if (userInformation.userType == UserType.ADMIN.name) {
                        tvHistoryLog.isVisible = true
                        tvSales.isVisible = true
                    }

                    // Instantiate view
                    Glide.with(requireView())
                        .load(user.avatarUrl)
                        .circleCrop()
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .error(R.drawable.ic_user)
                        .into(imgAvatar)

                    val fullName = "${user.firstName} ${user.lastName}"
                    tvUsername.text = fullName
                }
            }
        }
    }

    private fun getFirebaseUser() = FirebaseAuth.getInstance().currentUser

    private fun showSignOutDialog(auth: FirebaseAuth) {
        AlertDialog.Builder(requireContext())
            .setTitle("SIGN OUT")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton(
                "YES"
            ) { dialog, _ ->
                signOut(auth)
                dialog.dismiss()
            }
            .setNegativeButton("NO") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun signOut(auth: FirebaseAuth) {
        viewModel.signOut(auth)
    }

    private fun resetInformation() {
        binding.apply {
            tvUsername.text = resources.getString(R.string.label_guest)
            imgAvatar.setImageResource(R.drawable.ic_user)

            btnSignInAndOut.text = getString(R.string.btn_sign_in)
            cardViewBanner.isVisible = false

            tvAddress.isVisible = false
            tvProfile.isVisible = false
            tvWishList.isVisible = false
        }
    }

    private fun showSignInMethods() {

        // Choose authentication providers
        val providers = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().build(),
            AuthUI.IdpConfig.GoogleBuilder().build()
        )

        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .setTheme(R.style.ThemeOverlay_MaterialComponents_Dark)
                .build(),
            RC_SIGN_IN
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN && resultCode == Activity.RESULT_OK) {
            // Successfully signed in
            val user = getFirebaseUser()
            if (user != null) {
                // Try to send email verification for the first time the user uses the app.
                if (!user.isEmailVerified)
                    user.sendEmailVerification()

                loadingDialog.show()
                viewModel.fetchUserFromRemoteDb(user)
            }
        }
    }
}
