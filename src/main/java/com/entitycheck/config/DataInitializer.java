package com.entitycheck.config;

import com.entitycheck.model.*;
import com.entitycheck.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ClientCompanyRepository clientCompanyRepository;
    private final ProductRepository productRepository;
    private final ClientProductRepository clientProductRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           ClientCompanyRepository clientCompanyRepository,
                           ProductRepository productRepository,
                           ClientProductRepository clientProductRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.clientCompanyRepository = clientCompanyRepository;
        this.productRepository = productRepository;
        this.clientProductRepository = clientProductRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Create products
        Product ddr = null;
        Product lr = null;
        if (productRepository.count() == 0) {
            ddr = new Product();
            ddr.setName("Due Diligence Report");
            ddr.setCode("DDR");
            ddr.setDescription("Comprehensive company background investigation");
            ddr = productRepository.save(ddr);

            lr = new Product();
            lr.setName("Lien Report");
            lr.setCode("LR");
            lr.setDescription("Search for charges, liens, and encumbrances");
            lr = productRepository.save(lr);
        } else {
            ddr = productRepository.findAll().stream()
                    .filter(p -> "DDR".equals(p.getCode())).findFirst().orElse(null);
            lr = productRepository.findAll().stream()
                    .filter(p -> "LR".equals(p.getCode())).findFirst().orElse(null);
        }

        // Create client companies
        if (clientCompanyRepository.count() == 0) {
            ClientCompany acme = new ClientCompany();
            acme.setName("Acme Corporation");
            acme.setLegalName("Acme Corporation Inc.");
            acme.setSlug("acme");
            acme.setContactEmail("admin@acme.com");
            acme.setStatus(CompanyStatus.ACTIVE);
            acme = clientCompanyRepository.save(acme);

            ClientCompany beta = new ClientCompany();
            beta.setName("Beta Solutions");
            beta.setLegalName("Beta Solutions LLC");
            beta.setSlug("beta");
            beta.setContactEmail("admin@beta.com");
            beta.setStatus(CompanyStatus.ACTIVE);
            beta = clientCompanyRepository.save(beta);

            // Grant DDR product to both client companies
            if (ddr != null) {
                ClientProduct acmeDdr = new ClientProduct();
                acmeDdr.setClientCompany(acme);
                acmeDdr.setProduct(ddr);
                clientProductRepository.save(acmeDdr);

                ClientProduct betaDdr = new ClientProduct();
                betaDdr.setClientCompany(beta);
                betaDdr.setProduct(ddr);
                clientProductRepository.save(betaDdr);
            }
        }

        // Create users
        if (userRepository.count() == 0) {
            ClientCompany acme = clientCompanyRepository.findByName("Acme Corporation").orElseThrow();
            ClientCompany beta = clientCompanyRepository.findByName("Beta Solutions").orElseThrow();

            User superAdmin = new User();
            superAdmin.setEmail("super_admin@demo.com");
            superAdmin.setFullName("Super Admin");
            superAdmin.setPassword(passwordEncoder.encode("password123"));
            superAdmin.setRole(Role.SUPER_ADMIN);
            userRepository.save(superAdmin);

            User clientAdmin = new User();
            clientAdmin.setEmail("client_admin@acme.com");
            clientAdmin.setFullName("Client Admin");
            clientAdmin.setPassword(passwordEncoder.encode("password123"));
            clientAdmin.setRole(Role.CLIENT_ADMIN);
            clientAdmin.setClientCompany(acme);
            userRepository.save(clientAdmin);

            User clientUser = new User();
            clientUser.setEmail("client_user@acme.com");
            clientUser.setFullName("Client User");
            clientUser.setPassword(passwordEncoder.encode("password123"));
            clientUser.setRole(Role.CLIENT_USER);
            clientUser.setClientCompany(acme);
            userRepository.save(clientUser);

            User opsUser = new User();
            opsUser.setEmail("ops_user@demo.com");
            opsUser.setFullName("Operations User");
            opsUser.setPassword(passwordEncoder.encode("password123"));
            opsUser.setRole(Role.OPERATIONS_USER);
            userRepository.save(opsUser);
        }
    }
}
