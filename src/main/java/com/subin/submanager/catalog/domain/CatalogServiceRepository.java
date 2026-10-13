package com.subin.submanager.catalog.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CatalogServiceRepository extends JpaRepository<CatalogService, Long> {

    List<CatalogService> findAllByActiveTrueOrderByDisplayOrderAsc();
}
