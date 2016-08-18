package pt.ist.registration.process.ui;

import java.io.IOException;

import org.fenixedu.academic.domain.ExecutionYear;
import org.fenixedu.academic.domain.student.Registration;
import org.fenixedu.bennu.RegistrationProcessConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.google.common.base.Joiner;

import io.jsonwebtoken.Jwts;
import pt.ist.registration.process.ui.exception.UnauthorizedException;
import pt.ist.registration.process.ui.service.SignCertAndStoreService;

@RestController
@RequestMapping("/registration-process/registration-declarations")
public class SignedDeclarationsController {

    private final SignCertAndStoreService signCertAndStoreService;

    @Autowired
    public SignedDeclarationsController(SignCertAndStoreService signCertAndStoreService) {
        this.signCertAndStoreService = signCertAndStoreService;
    }

    @RequestMapping(method = RequestMethod.POST, value = "sign/{registration}")
    public String signCallback(@PathVariable Registration registration, @RequestParam MultipartFile file,
            @RequestParam String nounce) throws IOException {
        String uniqueIdentifier = Jwts.parser().setSigningKey(RegistrationProcessConfiguration.signerJwtSecret())
                .parseClaimsJws(nounce).getBody().getSubject();
        if (registration.getRegistrationDeclarationFile().getUniqueIdentifier().equals(uniqueIdentifier)) {
            signCertAndStoreService.sendDocumentToBeCertified(registration.getExternalId(), getFilename(registration), file,
                    uniqueIdentifier);
            return "ok";
        }
        throw new UnauthorizedException();
    }

    @RequestMapping(method = RequestMethod.POST, value = "cert/{registration}")
    public String certifierCallback(@PathVariable Registration registration, @RequestParam MultipartFile file,
            @RequestParam String nounce) throws IOException {
        String uniqueIdentifier = Jwts.parser().setSigningKey(RegistrationProcessConfiguration.certifierJwtSecret())
                .parseClaimsJws(nounce).getBody().getSubject();
        if (registration.getRegistrationDeclarationFile().getUniqueIdentifier().equals(uniqueIdentifier)) {
            signCertAndStoreService.sendDocumentToBeStored(registration.getPerson().getUsername(),
                    registration.getPerson().getEmailForSendingEmails(), getFilename(registration), file, uniqueIdentifier);
            return "ok";
        }
        throw new UnauthorizedException();
    }

    private String getStartYear(Registration registration) {
        ExecutionYear startExecutionYear = registration.getStartExecutionYear();
        if (startExecutionYear == null) {
            startExecutionYear = ExecutionYear.readCurrentExecutionYear();
        }
        return startExecutionYear.getQualifiedName().replaceAll("/", "-");
    }

    private String getFilename(Registration registration) {
        return Joiner.on("_").join("declaracao", registration.getDegree().getSigla(), getStartYear(registration),
                registration.getNumber()) + ".pdf";
    }

}