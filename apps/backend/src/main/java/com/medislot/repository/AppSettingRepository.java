package com.medislot.repository;

import com.medislot.entity.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {

    List<AppSetting> findAllByOrderBySettingKeyAsc();
}
