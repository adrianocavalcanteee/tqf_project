package com.tqfapp.android.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.tqfapp.android.R
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.databinding.ActivityHomeBinding
import com.tqfapp.android.ui.auth.LoginActivity
import com.tqfapp.android.ui.contracts.ContractsFragment
import com.tqfapp.android.ui.profile.ProfileFragment
import com.tqfapp.android.utils.UserSessionManager

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private var isProviderUser: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityHomeBinding.inflate(layoutInflater)
            setContentView(binding.root)

            val currentUser = UserRepository(this).getCurrentUser()
            isProviderUser = currentUser?.isProvider 
                ?: intent.getBooleanExtra(LoginActivity.EXTRA_IS_PROVIDER, UserSessionManager.isProviderUser(this))

            if (savedInstanceState == null) {
                if (isProviderUser) {
                    replaceFragment(ProviderHomeFragment())
                } else {
                    replaceFragment(HomeFragment())
                }
            }

            setupBottomNavigation()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupBottomNavigation() {
        binding.btnNavHome.setOnClickListener {
            if (isProviderUser) {
                replaceFragment(ProviderHomeFragment())
            } else {
                replaceFragment(HomeFragment())
            }
        }

        binding.btnNavContracts.setOnClickListener {
            replaceFragment(ContractsFragment())
        }

        binding.btnNavProfile.setOnClickListener {
            if (isProviderUser) {
                replaceFragment(ProfessionalProfileFragment.newInstance(isOwner = true))
            } else {
                replaceFragment(ProfileFragment())
            }
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
