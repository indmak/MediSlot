package com.medislot.service;

import com.medislot.dto.DepartmentForm;
import com.medislot.entity.Department;
import com.medislot.exception.BusinessException;
import com.medislot.repository.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 科室业务。
 */
@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public List<Department> list() {
        return departmentRepository.findAllByOrderBySortOrderAsc();
    }

    @Transactional
    public Department create(DepartmentForm form) {
        departmentRepository.findByName(form.getName()).ifPresent(d -> {
            throw new BusinessException("科室已存在：" + form.getName());
        });
        int order = form.getSortOrder() == null ? 0 : form.getSortOrder();
        return departmentRepository.save(new Department(form.getName(), order));
    }
}
