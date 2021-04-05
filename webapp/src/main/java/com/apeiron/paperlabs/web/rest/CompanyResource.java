package com.apeiron.paperlabs.web.rest;

import com.apeiron.paperlabs.domain.Company;
import com.apeiron.paperlabs.repository.CompanyRepository;
import com.apeiron.paperlabs.repository.search.CompanySearchRepository;
import com.apeiron.paperlabs.web.rest.errors.BadRequestAlertException;

import io.github.jhipster.web.util.HeaderUtil;
import io.github.jhipster.web.util.PaginationUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.elasticsearch.index.query.QueryBuilders.*;

/**
 * REST controller for managing {@link com.apeiron.paperlabs.domain.Company}.
 */
@RestController
@RequestMapping("/api")
public class CompanyResource {

    private final Logger log = LoggerFactory.getLogger(CompanyResource.class);

    private static final String ENTITY_NAME = "company";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final CompanyRepository companyRepository;

    private final CompanySearchRepository companySearchRepository;

    public CompanyResource(CompanyRepository companyRepository, CompanySearchRepository companySearchRepository) {
        this.companyRepository = companyRepository;
        this.companySearchRepository = companySearchRepository;
    }

    /**
     * {@code POST  /companies} : Create a new company.
     *
     * @param company the company to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new company, or with status {@code 400 (Bad Request)} if the company has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("/companies")
    public ResponseEntity<Company> createCompany(@RequestBody Company company) throws URISyntaxException {
        log.debug("REST request to save Company : {}", company);
        if (company.getId() != null) {
            throw new BadRequestAlertException("A new company cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Company result = companyRepository.save(company);
        companySearchRepository.save(result);
        return ResponseEntity.created(new URI("/api/companies/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * {@code PUT  /companies} : Updates an existing company.
     *
     * @param company the company to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated company,
     * or with status {@code 400 (Bad Request)} if the company is not valid,
     * or with status {@code 500 (Internal Server Error)} if the company couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("/companies")
    public ResponseEntity<Company> updateCompany(@RequestBody Company company) throws URISyntaxException {
        log.debug("REST request to update Company : {}", company);
        if (company.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        Company result = companyRepository.save(company);
        companySearchRepository.save(result);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, company.getId().toString()))
            .body(result);
    }

    /**
     * {@code GET  /companies} : get all the companies.
     *

     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of companies in body.
     */
    @GetMapping("/companies")
    public List<Company> getAllCompanies() {
        log.debug("REST request to get all Companies");
        return companyRepository.findAll();
    }

    /**
     * {@code GET  /companies/:id} : get the "id" company.
     *
     * @param id the id of the company to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the company, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/companies/{id}")
    public ResponseEntity<Company> getCompany(@PathVariable String id) {
        log.debug("REST request to get Company : {}", id);
        Optional<Company> company = companyRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(company);
    }

    /**
     * {@code DELETE  /companies/:id} : delete the "id" company.
     *
     * @param id the id of the company to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/companies/{id}")
    public ResponseEntity<Void> deleteCompany(@PathVariable String id) {
        log.debug("REST request to delete Company : {}", id);
        companyRepository.deleteById(id);
        companySearchRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }

    /**
     * {@code SEARCH  /_search/companies?query=:query} : search for the company corresponding
     * to the query.
     *
     * @param query the query of the company search.
     * @return the result of the search.
     */
    @GetMapping("/_search/companies")
    public List<Company> searchCompanies(@RequestParam String query) {
        log.debug("REST request to search Companies for query {}", query);
        return StreamSupport
            .stream(companySearchRepository.search(queryStringQuery(query)).spliterator(), false)
            .collect(Collectors.toList());
    }
    /**
     * get all the companies by userId.
     *
     * @param userId the userId.
     * @return List of companies.
     */
    @GetMapping("/companies/user/{userId}")
    public ResponseEntity<List<Company>> searchCompaniesByUser(@PathVariable String userId, @RequestParam Optional<Integer> pageNumber, @RequestParam Optional<Integer> itemsPerPage) {
        log.debug("REST request to get all companies by user {}",userId);
        if(pageNumber.isPresent() && itemsPerPage.isPresent()) {
            Pageable pageable = PageRequest.of(pageNumber.get(), itemsPerPage.get());
            Page<Company> page = companyRepository.getAllByUserIdEquals(userId, pageable);
            HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
            return ResponseEntity.ok().headers(headers).body(page.getContent());
        }
        return ResponseEntity.ok().body(companyRepository.getAllByUserIdEquals(userId));
    }
}
