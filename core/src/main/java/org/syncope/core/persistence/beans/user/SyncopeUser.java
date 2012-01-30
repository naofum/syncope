/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */
package org.syncope.core.persistence.beans.user;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.persistence.Basic;
import javax.persistence.Cacheable;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.Lob;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.security.crypto.codec.Base64;
import org.syncope.core.persistence.beans.AbstractAttr;
import org.syncope.core.persistence.beans.AbstractAttributable;
import org.syncope.core.persistence.beans.AbstractDerAttr;
import org.syncope.core.persistence.beans.AbstractVirAttr;
import org.syncope.core.persistence.beans.ExternalResource;
import org.syncope.core.persistence.beans.membership.Membership;
import org.syncope.core.persistence.beans.role.SyncopeRole;
import org.syncope.core.persistence.validation.entity.SyncopeUserCheck;
import org.syncope.types.CipherAlgorithm;

@Entity
@Cacheable
@SyncopeUserCheck
public class SyncopeUser extends AbstractAttributable {

    private static final long serialVersionUID = -3905046855521446823L;

    private static SecretKeySpec keySpec;

    static {
        try {
            keySpec = new SecretKeySpec(ArrayUtils.subarray(
                    "1abcdefghilmnopqrstuvz2!".getBytes("UTF8"), 0, 16), "AES");
        } catch (Exception e) {
            LOG.error("Error during key specification", e);
        }
    }

    @Id
    private Long id;

    @NotNull
    private String password;

    @Transient
    private String clearPassword;

    @OneToMany(cascade = CascadeType.MERGE, mappedBy = "syncopeUser")
    @Valid
    private List<Membership> memberships;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "owner")
    @Valid
    private List<UAttr> attributes;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "owner")
    @Valid
    private List<UDerAttr> derivedAttributes;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "owner")
    @Valid
    private List<UVirAttr> virtualAttributes;

    private String workflowId;

    @Column(nullable = true)
    private String status;

    @Lob
    private String token;

    @Temporal(TemporalType.TIMESTAMP)
    private Date tokenExpireTime;

    @Column(nullable = true)
    @Enumerated(EnumType.STRING)
    private CipherAlgorithm cipherAlgorithm;

    @ElementCollection
    private List<String> passwordHistory;

    /**
     * Subsequent failed logins.
     */
    @Column(nullable = true)
    private Integer failedLogins;

    /**
     * Username/Login.
     */
    @Column(unique = true)
    @NotNull
    private String username;

    /**
     * Last successful login date.
     */
    @Column(nullable = true)
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastLoginDate;

    /**
     * Creation date.
     */
    @NotNull
    @Temporal(TemporalType.TIMESTAMP)
    private Date creationDate;

    /**
     * Change password date.
     */
    @Column(nullable = true)
    @Temporal(TemporalType.TIMESTAMP)
    private Date changePwdDate;

    @Basic
    @Min(0)
    @Max(1)
    private Integer suspended;

    /**
     * Provisioning external resources.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(joinColumns =
    @JoinColumn(name = "user_id"),
    inverseJoinColumns =
    @JoinColumn(name = "resource_name"))
    private Set<ExternalResource> resources;

    public SyncopeUser() {
        super();

        memberships = new ArrayList<Membership>();
        attributes = new ArrayList<UAttr>();
        derivedAttributes = new ArrayList<UDerAttr>();
        virtualAttributes = new ArrayList<UVirAttr>();
        passwordHistory = new ArrayList<String>();
        failedLogins = 0;
        suspended = getBooleanAsInteger(Boolean.FALSE);
        resources = new HashSet<ExternalResource>();
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    protected Set<ExternalResource> resources() {
        return resources;
    }

    public boolean addMembership(final Membership membership) {
        return memberships.contains(membership) || memberships.add(membership);
    }

    public boolean removeMembership(final Membership membership) {
        return memberships.remove(membership);
    }

    public Membership getMembership(final Long syncopeRoleId) {
        Membership result = null;
        Membership membership;
        for (Iterator<Membership> itor = getMemberships().iterator();
                result == null && itor.hasNext();) {

            membership = itor.next();
            if (membership.getSyncopeRole() != null && syncopeRoleId.equals(
                    membership.getSyncopeRole().getId())) {

                result = membership;
            }
        }

        return result;
    }

    public List<Membership> getMemberships() {
        return memberships;
    }

    public void setMemberships(final List<Membership> memberships) {
        this.memberships.clear();
        if (memberships != null && !memberships.isEmpty()) {
            this.memberships.addAll(memberships);
        }
    }

    public List<SyncopeRole> getRoles() {
        List<SyncopeRole> result = new ArrayList<SyncopeRole>();

        for (Membership membership : memberships) {
            if (membership.getSyncopeRole() != null) {
                result.add(membership.getSyncopeRole());
            }
        }

        return result;
    }

    public Set<Long> getRoleIds() {
        List<SyncopeRole> roles = getRoles();

        Set<Long> result = new HashSet<Long>(roles.size());
        for (SyncopeRole role : roles) {
            result.add(role.getId());
        }

        return result;
    }

    @Override
    public Set<ExternalResource> getResources() {
        Set<ExternalResource> result = new HashSet<ExternalResource>();
        result.addAll(super.getResources());
        for (SyncopeRole role : getRoles()) {
            result.addAll(role.getResources());
        }

        return result;
    }

    public String getPassword() {
        return password;
    }

    public String getClearPassword() {
        return clearPassword;
    }

    public void removeClearPassword() {
        clearPassword = null;
    }

    public void setPassword(final String password,
            final CipherAlgorithm cipherAlgoritm, final int historySize) {

        // clear password
        clearPassword = password;

        try {
            this.password = encodePassword(password, cipherAlgoritm);
            this.cipherAlgorithm = cipherAlgoritm;
        } catch (Throwable t) {
            LOG.error("Could not encode password", t);
            this.password = null;
        }
    }

    @Override
    public <T extends AbstractAttr> boolean addAttribute(final T attribute) {
        return attributes.add((UAttr) attribute);
    }

    @Override
    public <T extends AbstractAttr> boolean removeAttribute(final T attribute) {
        return attributes.remove((UAttr) attribute);
    }

    @Override
    public List<? extends AbstractAttr> getAttributes() {
        return attributes;
    }

    @Override
    public void setAttributes(final List<? extends AbstractAttr> attributes) {
        this.attributes.clear();
        if (attributes != null && !attributes.isEmpty()) {
            this.attributes.addAll((List<UAttr>) attributes);
        }
    }

    @Override
    public <T extends AbstractDerAttr> boolean addDerivedAttribute(
            final T derivedAttribute) {

        return derivedAttributes.add((UDerAttr) derivedAttribute);
    }

    @Override
    public <T extends AbstractDerAttr> boolean removeDerivedAttribute(
            T derivedAttribute) {

        return derivedAttributes.remove((UDerAttr) derivedAttribute);
    }

    @Override
    public List<? extends AbstractDerAttr> getDerivedAttributes() {
        return derivedAttributes;
    }

    @Override
    public void setDerivedAttributes(
            final List<? extends AbstractDerAttr> derivedAttributes) {

        this.derivedAttributes.clear();
        if (derivedAttributes != null && !derivedAttributes.isEmpty()) {
            this.derivedAttributes.addAll((List<UDerAttr>) derivedAttributes);
        }
    }

    @Override
    public <T extends AbstractVirAttr> boolean addVirtualAttribute(
            final T virtualAttribute) {

        return virtualAttributes.add((UVirAttr) virtualAttribute);
    }

    @Override
    public <T extends AbstractVirAttr> boolean removeVirtualAttribute(
            final T virtualAttribute) {

        return virtualAttributes.remove((UVirAttr) virtualAttribute);
    }

    @Override
    public List<? extends AbstractVirAttr> getVirtualAttributes() {
        return virtualAttributes;
    }

    @Override
    public void setVirtualAttributes(
            final List<? extends AbstractVirAttr> virtualAttributes) {

        this.virtualAttributes.clear();
        if (virtualAttributes != null && !virtualAttributes.isEmpty()) {
            this.virtualAttributes.addAll((List<UVirAttr>) virtualAttributes);
        }
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(String workflowId) {
        this.workflowId = workflowId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void generateToken(int tokenLength, int tokenExpireTime) {
        this.token = RandomStringUtils.randomAlphanumeric(tokenLength);

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, tokenExpireTime);
        this.tokenExpireTime = calendar.getTime();
    }

    public void removeToken() {
        this.token = null;
        this.tokenExpireTime = null;
    }

    public String getToken() {
        return token;
    }

    public Date getTokenExpireTime() {
        return tokenExpireTime == null
                ? null : new Date(tokenExpireTime.getTime());
    }

    public boolean checkToken(final String token) {
        return this.token == null || (this.token.equals(token)
                && tokenExpireTime.after(new Date()));
    }

    public CipherAlgorithm getCipherAlgoritm() {
        return cipherAlgorithm;
    }

    public void setCipherAlgoritm(final CipherAlgorithm cipherAlgoritm) {
        this.cipherAlgorithm = cipherAlgoritm;
    }

    public List<String> getPasswordHistory() {
        return passwordHistory;
    }

    public Date getChangePwdDate() {
        return changePwdDate == null ? null : new Date(changePwdDate.getTime());
    }

    public void setChangePwdDate(final Date changePwdDate) {
        this.changePwdDate = changePwdDate == null
                ? null : new Date(changePwdDate.getTime());
    }

    public Date getCreationDate() {
        return creationDate == null ? null : new Date(creationDate.getTime());
    }

    public void setCreationDate(final Date creationDate) {
        this.creationDate = creationDate == null
                ? null : new Date(creationDate.getTime());
    }

    public Integer getFailedLogins() {
        return failedLogins != null ? failedLogins : 0;
    }

    public void setFailedLogins(final Integer failedLogins) {
        this.failedLogins = failedLogins;
    }

    public Date getLastLoginDate() {
        return lastLoginDate == null ? null : new Date(lastLoginDate.getTime());
    }

    public void setLastLoginDate(final Date lastLoginDate) {
        this.lastLoginDate = lastLoginDate == null
                ? null : new Date(lastLoginDate.getTime());
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public void setSuspended(final Boolean suspended) {
        this.suspended = getBooleanAsInteger(suspended);
    }

    public Boolean getSuspended() {
        return isBooleanAsInteger(suspended);
    }

    private String encodePassword(
            final String password, final CipherAlgorithm cipherAlgoritm)
            throws UnsupportedEncodingException, NoSuchAlgorithmException,
            NoSuchPaddingException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException {

        String encodedPassword = null;

        if (password != null) {
            if (cipherAlgoritm == null
                    || cipherAlgoritm == CipherAlgorithm.AES) {

                final byte[] cleartext = password.getBytes("UTF8");

                final Cipher cipher = Cipher.getInstance(
                        CipherAlgorithm.AES.getAlgorithm());

                cipher.init(Cipher.ENCRYPT_MODE, keySpec);

                byte[] encoded = cipher.doFinal(cleartext);

                encodedPassword = new String(Base64.encode(encoded));
            } else {
                MessageDigest algorithm = MessageDigest.getInstance(
                        cipherAlgoritm.getAlgorithm());

                algorithm.reset();
                algorithm.update(password.getBytes());

                byte[] messageDigest = algorithm.digest();

                StringBuilder hexString = new StringBuilder();
                for (int i = 0; i < messageDigest.length; i++) {
                    String hex = Integer.toHexString(0xff & messageDigest[i]);
                    if (hex.length() == 1) {
                        hexString.append('0');
                    }
                    hexString.append(hex);
                }

                encodedPassword = hexString.toString();
            }
        }

        return encodedPassword;
    }

    public boolean verifyPasswordHistory(final String password,
            final int size) {

        boolean res = false;

        if (size > 0) {
            try {
                res = passwordHistory.subList(
                        size >= passwordHistory.size() ? 0
                        : passwordHistory.size() - size,
                        passwordHistory.size()).contains(cipherAlgorithm != null
                        ? encodePassword(password, cipherAlgorithm) : password);
            } catch (Throwable t) {
                LOG.error("Error evaluating password history", t);
            }
        }

        return res;
    }
}
