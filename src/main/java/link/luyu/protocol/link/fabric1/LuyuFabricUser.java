package link.luyu.protocol.link.fabric1;

import static com.webank.wecross.common.FabricType.STUB_NAME;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webank.wecross.stub.ObjectMapperFactory;
import java.security.PrivateKey;
import java.util.Set;
import link.luyu.protocol.network.Account;
import link.luyu.protocol.network.Path;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuyuFabricUser implements User {
    private static ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();
    private static Logger logger = LoggerFactory.getLogger(LuyuFabricUser.class);
    private Account luyuAccount;
    private String chainPath;

    public LuyuFabricUser(Account luyuAccount, String chainPath) {
        this.luyuAccount = luyuAccount;
        Path path = Path.decode(chainPath);
        path.setResource(null);
        this.chainPath = path.toString();
    }

    private String prefix(String name) {
        return prefix(name, this.chainPath);
    }

    public static String prefix(String name, String chainPath) {
        return STUB_NAME + ":" + chainPath + ":" + name.toLowerCase();
    }

    @Override
    public String getName() {
        return luyuAccount.getProperty(prefix("name"));
    }

    @Override
    public Set<String> getRoles() {
        return null;
    }

    @Override
    public String getAccount() {
        return null;
    }

    @Override
    public String getAffiliation() {
        return null;
    }

    @Override
    public Enrollment getEnrollment() {
        try {
            String certStr = luyuAccount.getProperty(prefix("cert"));
            if (certStr == null) {
                throw new Exception("get cert property return null");
            } else {
                EnrollmentImpl enrollment = new EnrollmentImpl();
                enrollment.setCert(certStr);
                enrollment.setKey(null); // no need to get key
                return enrollment;
            }
        } catch (Exception e) {
            String errorMessage =
                    "getEnrollment failed, please add account properties for " + prefix("");
            logger.error(errorMessage, e);
            throw new RuntimeException(errorMessage);
        }
    }

    @Override
    public String getMspId() {
        return luyuAccount.getProperty(prefix("mspid"));
    }

    public static class EnrollmentImpl implements Enrollment {
        private PrivateKey key;
        private String cert;

        @Override
        public PrivateKey getKey() {
            return key;
        }

        @Override
        public String getCert() {
            return cert;
        }

        public void setKey(PrivateKey key) {
            this.key = key;
        }

        public void setCert(String cert) {
            this.cert = cert;
        }
    }
}
