package com.apeiron.paperlabs.facade;

import com.apeiron.docflow.domain.InputData;
import com.apeiron.docflow.service.DocumentGenerationService;
import com.apeiron.paperlabs.config.ApplicationProperties;
import com.apeiron.paperlabs.domain.Company;
import com.apeiron.paperlabs.domain.Employer;
import com.apeiron.paperlabs.domain.Order;
import com.apeiron.paperlabs.domain.User;
import com.apeiron.paperlabs.domain.enumeration.OrderStatus;
import com.apeiron.paperlabs.repository.CompanyRepository;
import com.apeiron.paperlabs.repository.EmployerRepository;
import com.apeiron.paperlabs.repository.OrderRepository;
import com.apeiron.paperlabs.repository.UserRepository;
import com.apeiron.paperlabs.service.LegalDocumentService;
import com.apeiron.paperlabs.service.dto.GeneratedLegalDocumentDTO;
import com.apeiron.paperlabs.service.dto.LegalDocumentDTO;
import com.apeiron.paperlabs.service.dto.OrderDTO;
import com.apeiron.paperlabs.service.dto.UserDTO;
import com.apeiron.paperlabs.service.mapper.OrderMapper;
import com.apeiron.paperlabs.service.mapper.UserMapper;
import com.apeiron.paperlabs.service.mapper.documents.StepperDataToEntityMapper;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.*;

@Service
public class DocumentGenerationFacade {
    private final String TEMPLATE_STEPPER_CONFIG_PATH_PREFIX = "../webapp/webapp/template-config/config/stepper/";
    private final String TEMPLATE_WORKFLOW_CONFIG_PATH_PREFIX = "../webapp/webapp/template-config/config/workflow/";
    private final String TEMPLATE_PATH_PREFIX = "../webapp/webapp/template-config/template/";
    private final String COMPANY_NAME_FIELD_NAME = "companyName";
    private final String EMPLOYER_CIN_NUMBER_FIELD_NAME = "employerCinNumber";

    private final DocumentGenerationService documentGenerationService;
    private final LegalDocumentService legalDocumentService;
    private final CompanyRepository companyRepository;
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final StepperDataToEntityMapper stepperDataToEntityMapper;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final ApplicationProperties applicationProperties;
    private final EmployerRepository employerRepository;

    public DocumentGenerationFacade(
        DocumentGenerationService documentGenerationService,
        LegalDocumentService legalDocumentService,
        CompanyRepository companyRepository,
        EmployerRepository employerRepository,
        OrderRepository orderRepository,
        OrderMapper orderMapper,
        StepperDataToEntityMapper stepperDataToEntityMapper,
        UserRepository userRepository,
        UserMapper userMapper,
        ApplicationProperties applicationProperties
    ) {
        this.documentGenerationService = documentGenerationService;
        this.legalDocumentService = legalDocumentService;
        this.companyRepository = companyRepository;
        this.employerRepository = employerRepository;
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.stepperDataToEntityMapper = stepperDataToEntityMapper;
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.applicationProperties = applicationProperties;
    }

    public OrderDTO generateDocXFile(Map<String, InputData> stepperDataDTO, String legalDocumentId, boolean saveDataAuthorization, String userLogin, String orderId) {

        Optional<LegalDocumentDTO> legalDocument = legalDocumentService.findOne(legalDocumentId);
        String generatedLegalDocumentURL = null ;
        OrderDTO orderDTO = new OrderDTO();
        if (orderId != null && !"undefined".equals(orderId)) {
            orderDTO.setId(orderId);
        }
        orderDTO.setDowloadHistories(new ArrayList<>());
        GeneratedLegalDocumentDTO generatedLegalDocumentDTO = new GeneratedLegalDocumentDTO();

        if(legalDocument.isPresent()) {
            URL stepperFileUrl = null;
            URL workflowFileUrl = null;
            try {
                stepperFileUrl = new File(TEMPLATE_STEPPER_CONFIG_PATH_PREFIX+legalDocument.get().getStepperConfigFilePath()).toURI().toURL();
                workflowFileUrl = new File(TEMPLATE_WORKFLOW_CONFIG_PATH_PREFIX+legalDocument.get().getWorkflowConfigFilePath()).toURI().toURL();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            String documentFileUrl = TEMPLATE_PATH_PREFIX+legalDocument.get().getTemplateFilePath();
            try {
                generatedLegalDocumentURL = documentGenerationService.generateDocXFile(stepperDataDTO, stepperFileUrl, documentFileUrl, workflowFileUrl);

            } catch (IOException e) {
                e.getMessage();
            }
            generatedLegalDocumentDTO.setGeneratedWordFilePath(generatedLegalDocumentURL);
            generatedLegalDocumentDTO.setDate(Instant.now());

            UserDTO userDTO;

            Optional<User> user = userRepository.findOneByLogin(userLogin);
            if (user.isPresent()) {
                userDTO = userMapper.userToUserDTO(user.get());
            }
            else {
                throw new UsernameNotFoundException(String.format("No user found with login : %s", userLogin));
            }

            orderDTO.setGeneratedLegalDocument(generatedLegalDocumentDTO);
            orderDTO.setPrice(legalDocument.get().getPrice());
            orderDTO.setStatus(OrderStatus.WAITING);
            orderDTO.setLegalDocument(legalDocument.get());
            orderDTO.setTotalPrice(
                (float) (legalDocument.get().getPrice()
                    + ((legalDocument.get().getPrice() * applicationProperties.getCompanyInvoiceTva()) / 100 ))
            );
            orderDTO.setUser(userDTO);
            orderDTO.setTax(new Float(applicationProperties.getCompanyInvoiceTva()));

            // TODO: create service to save Orders
            Order order = orderMapper.toEntity(orderDTO);
            order = orderRepository.save(order);
            OrderDTO result = orderMapper.toDto(order);
            orderRepository.save(order);

            if(saveDataAuthorization) {
              // if (("CDI").equals(legalDocument.get().getShortName()) || ("NDA").equals(legalDocument.get().getShortName())) {
                    // all inputs in stepper form must have a value
                    List<Map<String, String>> companiesAutoFillInputIdsByFieldName = legalDocument.get().getCompaniesAutoFillInputIdsByFieldName();
                    List<Map<String, String>> employersAutoFillInputIdsByFieldName = legalDocument.get().getEmployersAutoFillInputIdsByFieldName();

                    //company
                    if (!companiesAutoFillInputIdsByFieldName.isEmpty()) {
                        companiesAutoFillInputIdsByFieldName.forEach(companyAutoFillInputIdsByFieldName -> {
                            if(stepperDataDTO.get(companyAutoFillInputIdsByFieldName.get(COMPANY_NAME_FIELD_NAME)) != null) {
                                String companyName = stepperDataDTO.get(companyAutoFillInputIdsByFieldName.get(COMPANY_NAME_FIELD_NAME)).getValue();
                                Optional<Company> company = companyRepository.findByCompanyNameEquals(companyName);
                                Company newCompany;

                                if (company.isPresent()) {
                                    newCompany = company.get();
                                }
                                else {
                                    newCompany = new Company();
                                    newCompany.setUserId(userLogin);
                                }
                                newCompany = (Company) this.stepperDataToEntityMapper.stepperDataToCompany(newCompany, companyAutoFillInputIdsByFieldName, stepperDataDTO);
                                companyRepository.save(newCompany);
                            }
                        });
                    }
                    //employer
                  /*  if (("CDI").equals(legalDocument.get().getShortName()) && !employersAutoFillInputIdsByFieldName.isEmpty()) {
                        employersAutoFillInputIdsByFieldName.forEach(employerAutoFillInputIdsByFieldName -> {
                            if(stepperDataDTO.get(employerAutoFillInputIdsByFieldName.get(EMPLOYER_CIN_NUMBER_FIELD_NAME)) != null) {
                                String employerCinNumber = stepperDataDTO.get(employerAutoFillInputIdsByFieldName.get(EMPLOYER_CIN_NUMBER_FIELD_NAME)).getValue();
                                Optional<Employer> employer = employerRepository.findByEmployerCinNumberEquals(Long.parseLong(employerCinNumber));
                                Employer newEmployer;

                                if (employer.isPresent()) {
                                    newEmployer = employer.get();
                                } else {
                                    newEmployer = new Employer();
                                    newEmployer.setUserId(userLogin);
                                }
                                newEmployer = (Employer) this.stepperDataToEntityMapper.stepperDataToCompany(newEmployer, employerAutoFillInputIdsByFieldName, stepperDataDTO);
                                employerRepository.save(newEmployer);
                            }
                        });
                   /* }
               // }
              /*  if (("SARL").equals(legalDocument.get().getShortName())) {

                    // company
                    String companyName = stepperDataDTO.get("input_dénomination_de_la_société").getValue();
                    List<String> companyFoundingPartners = new ArrayList<>();
                    Company newCompany = new Company();
                    Optional<Company> company = companyRepository.findByCompanyNameEquals(companyName);
                    if (company.isPresent()) {
                        throw new RuntimeException("Company name already exists.");
                    }

                    companyFoundingPartners.add(stepperDataDTO.get("input_nomPrénom_associé_fondateur1").getValue());
                    companyFoundingPartners.add(stepperDataDTO.get("input_nomPrénom_associé_fondateur2").getValue());

                    newCompany.setCompanyName(companyName);
                    newCompany.setCompanyCapital(Long.parseLong(stepperDataDTO.get("input_capital_social_de_la_société_chiffres").getValue()));
                    newCompany.setCompanyManagerFullName(stepperDataDTO.get("input_nomPrénom_Manager").getValue());
                    newCompany.setCompanyFullAddress(stepperDataDTO.get("input_adresse_de_la_société").getValue());
                    newCompany.setCompanyType(legalDocument.get().getShortName());
                    newCompany.setCompanyActivityArea(stepperDataDTO.get("input_domaine_activité_société").getValue());
                    newCompany.setUserId(userLogin);
                    newCompany.setCompanyFoundingPartners(companyFoundingPartners);
                    if (("non_exportatrice").equals(stepperDataDTO.get("input_exportabilité_société").getValue())) {
                        newCompany.setCompanyExportability("non exportatrice");
                    }
                    else if (("partiellement_exportatrice").equals(stepperDataDTO.get("input_exportabilité_société").getValue())) {
                        newCompany.setCompanyExportability("partiellement exportatrice");
                    }
                    else if (("totalement_exportatrice").equals(stepperDataDTO.get("input_exportabilité_société").getValue())) {
                        newCompany.setCompanyExportability("totalement exportatrice");
                    }
                    if (("résidente").equals(stepperDataDTO.get("input_juridiction_société").getValue())) {
                        newCompany.setCompanyJurisdiction("résidente");
                    }
                    else if (("offshore").equals(stepperDataDTO.get("input_juridiction_société").getValue())) {
                        newCompany.setCompanyJurisdiction("offshore");
                    }

                    companyRepository.save(newCompany);
                } */
             /*   else if(("PV-NNG").equals(legalDocument.get().getShortName())){

                }*/
            }
            return result;
       } else {
            throw new NoSuchElementException("No legal document found with id : "+legalDocumentId);
        }
    }

    public Boolean currentUserHasConcernedEntityObjects(String documentId, String userLogin) {
        boolean result = false;
        List<Company> userCompanies = this.companyRepository.getAllByUserIdEquals(userLogin);
         List<Employer> userEmployers = this.employerRepository.findAllByUserIdEquals(userLogin);

        List<String> autoFillConcernedEntities;
        Optional<LegalDocumentDTO> legalDocument = legalDocumentService.findOne(documentId);
        if (legalDocument.isPresent()) {
            autoFillConcernedEntities = legalDocument.get().getAutoFillConcernedEntities();
            for(String autoFillConcernedEntity: autoFillConcernedEntities) {
                if ("Company".equals(autoFillConcernedEntity)) {
                    if(!userCompanies.isEmpty()) {
                        result = true;
                    }
                }
                else if ("Employer".equals(autoFillConcernedEntity)) {
                    if(!userEmployers.isEmpty()) {
                        result = true;
                    }
                }
            }
        }
        else {
            throw new RuntimeException(String.format("Document with id %s not found.", documentId));
        }
        return result;
    }
}
