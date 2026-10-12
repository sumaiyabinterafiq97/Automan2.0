package com.automan.backend.repository

import com.automan.backend.model.AuthBootstrap
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AuthBootstrapRepository : JpaRepository<AuthBootstrap, Short>
