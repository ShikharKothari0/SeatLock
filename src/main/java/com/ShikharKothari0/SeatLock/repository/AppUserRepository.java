package com.ShikharKothari0.SeatLock.repository;

import com.ShikharKothari0.SeatLock.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
}
