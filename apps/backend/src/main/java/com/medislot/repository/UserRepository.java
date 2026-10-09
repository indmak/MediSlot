package com.medislot.repository;

import com.medislot.entity.Role;
import com.medislot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPhone(String phone);

    boolean existsByPhone(String phone);

    long countByRole(Role role);

    List<User> findAllByRoleInOrderByCreatedAtDesc(List<Role> roles);
}
