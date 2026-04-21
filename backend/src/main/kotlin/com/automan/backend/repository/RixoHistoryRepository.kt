package com.automan.backend.repository

import com.automan.backend.model.RixoHistory
import org.springframework.data.jpa.repository.JpaRepository

interface RixoHistoryRepository : JpaRepository<RixoHistory, Long>
