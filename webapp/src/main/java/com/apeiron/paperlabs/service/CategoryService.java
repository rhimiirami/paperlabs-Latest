package com.apeiron.paperlabs.service;

import com.apeiron.paperlabs.service.dto.CategoryDTO;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Service Interface for managing {@link com.apeiron.paperlabs.domain.Category}.
 */
public interface CategoryService {

    /**
     * Save a category.
     *
     * @param categoryDTO the entity to save.
     * @return the persisted entity.
     */
    CategoryDTO save(CategoryDTO categoryDTO);

    /**
     * Get all the categories.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    Page<CategoryDTO> findAll(Pageable pageable);


    /**
     * Get the "id" category.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    Optional<CategoryDTO> findOne(String id);

    /**
     * Delete the "id" category.
     *
     * @param id the id of the entity.
     */
    void delete(String id);

    /**
     * Search for the category corresponding to the query.
     *
     * @param query the query of the search.
     * 
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    Page<CategoryDTO> search(String query, Pageable pageable);
}
