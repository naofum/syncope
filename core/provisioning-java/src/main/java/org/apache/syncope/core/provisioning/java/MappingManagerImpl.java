/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.syncope.core.provisioning.java;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.syncope.common.lib.to.AnyObjectTO;
import org.apache.syncope.common.lib.to.AnyTO;
import org.apache.syncope.common.lib.Attr;
import org.apache.syncope.common.lib.to.GroupTO;
import org.apache.syncope.common.lib.to.GroupableRelatableTO;
import org.apache.syncope.common.lib.to.MembershipTO;
import org.apache.syncope.common.lib.to.RealmTO;
import org.apache.syncope.common.lib.to.UserTO;
import org.apache.syncope.common.lib.types.AnyTypeKind;
import org.apache.syncope.common.lib.types.AttrSchemaType;
import org.apache.syncope.core.persistence.api.dao.AnyObjectDAO;
import org.apache.syncope.core.persistence.api.dao.AnyTypeDAO;
import org.apache.syncope.core.persistence.api.dao.ApplicationDAO;
import org.apache.syncope.core.persistence.api.dao.GroupDAO;
import org.apache.syncope.core.persistence.api.dao.RealmDAO;
import org.apache.syncope.core.persistence.api.dao.RelationshipTypeDAO;
import org.apache.syncope.core.persistence.api.dao.UserDAO;
import org.apache.syncope.core.persistence.api.entity.Any;
import org.apache.syncope.core.persistence.api.entity.AnyType;
import org.apache.syncope.core.persistence.api.entity.AnyUtils;
import org.apache.syncope.core.persistence.api.entity.AnyUtilsFactory;
import org.apache.syncope.core.persistence.api.entity.Application;
import org.apache.syncope.core.persistence.api.entity.Attributable;
import org.apache.syncope.core.persistence.api.entity.DerSchema;
import org.apache.syncope.core.persistence.api.entity.GroupableRelatable;
import org.apache.syncope.core.persistence.api.entity.Membership;
import org.apache.syncope.core.persistence.api.entity.PlainAttr;
import org.apache.syncope.core.persistence.api.entity.PlainAttrValue;
import org.apache.syncope.core.persistence.api.entity.PlainSchema;
import org.apache.syncope.core.persistence.api.entity.Realm;
import org.apache.syncope.core.persistence.api.entity.Relationship;
import org.apache.syncope.core.persistence.api.entity.RelationshipType;
import org.apache.syncope.core.persistence.api.entity.VirSchema;
import org.apache.syncope.core.persistence.api.entity.anyobject.AnyObject;
import org.apache.syncope.core.persistence.api.entity.group.Group;
import org.apache.syncope.core.persistence.api.entity.resource.Item;
import org.apache.syncope.core.persistence.api.entity.resource.Mapping;
import org.apache.syncope.core.persistence.api.entity.resource.MappingItem;
import org.apache.syncope.core.persistence.api.entity.resource.OrgUnit;
import org.apache.syncope.core.persistence.api.entity.resource.OrgUnitItem;
import org.apache.syncope.core.persistence.api.entity.resource.Provision;
import org.apache.syncope.core.persistence.api.entity.user.Account;
import org.apache.syncope.core.persistence.api.entity.user.LAPlainAttr;
import org.apache.syncope.core.persistence.api.entity.user.LinkedAccount;
import org.apache.syncope.core.persistence.api.entity.user.User;
import org.apache.syncope.core.provisioning.api.AccountGetter;
import org.apache.syncope.core.provisioning.api.DerAttrHandler;
import org.apache.syncope.core.provisioning.api.IntAttrName;
import org.apache.syncope.core.provisioning.api.MappingManager;
import org.apache.syncope.core.provisioning.api.PlainAttrGetter;
import org.apache.syncope.core.provisioning.api.VirAttrHandler;
import org.apache.syncope.core.provisioning.api.cache.VirAttrCache;
import org.apache.syncope.core.provisioning.java.utils.ConnObjectUtils;
import org.apache.syncope.core.provisioning.java.utils.MappingUtils;
import org.apache.syncope.core.spring.policy.InvalidPasswordRuleConf;
import org.apache.syncope.core.spring.security.Encryptor;
import org.apache.syncope.core.spring.security.PasswordGenerator;
import org.identityconnectors.framework.common.FrameworkUtil;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.apache.syncope.core.provisioning.api.data.ItemTransformer;
import org.identityconnectors.framework.common.objects.Name;

@Component
public class MappingManagerImpl implements MappingManager {

    private static final Logger LOG = LoggerFactory.getLogger(MappingManager.class);

    private static final Encryptor ENCRYPTOR = Encryptor.getInstance();

    @Autowired
    private AnyTypeDAO anyTypeDAO;

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private AnyObjectDAO anyObjectDAO;

    @Autowired
    private GroupDAO groupDAO;

    @Autowired
    private RelationshipTypeDAO relationshipTypeDAO;

    @Autowired
    private RealmDAO realmDAO;

    @Autowired
    private ApplicationDAO applicationDAO;

    @Autowired
    private DerAttrHandler derAttrHandler;

    @Autowired
    private VirAttrHandler virAttrHandler;

    @Autowired
    private VirAttrCache virAttrCache;

    @Autowired
    private PasswordGenerator passwordGenerator;

    @Autowired
    private AnyUtilsFactory anyUtilsFactory;

    @Autowired
    private IntAttrNameParser intAttrNameParser;

    protected String processPreparedAttr(final Pair<String, Attribute> preparedAttr, final Set<Attribute> attributes) {
        String connObjectKey = null;

        if (preparedAttr != null) {
            if (preparedAttr.getLeft() != null) {
                connObjectKey = preparedAttr.getLeft();
            }

            if (preparedAttr.getRight() != null) {
                Attribute alreadyAdded = AttributeUtil.find(preparedAttr.getRight().getName(), attributes);

                if (alreadyAdded == null) {
                    attributes.add(preparedAttr.getRight());
                } else {
                    attributes.remove(alreadyAdded);

                    Set<Object> values = new HashSet<>();
                    if (alreadyAdded.getValue() != null && !alreadyAdded.getValue().isEmpty()) {
                        values.addAll(alreadyAdded.getValue());
                    }

                    if (preparedAttr.getRight().getValue() != null) {
                        values.addAll(preparedAttr.getRight().getValue());
                    }

                    attributes.add(AttributeBuilder.build(preparedAttr.getRight().getName(), values));
                }
            }
        }

        return connObjectKey;
    }

    @Transactional(readOnly = true)
    @Override
    public Pair<String, Set<Attribute>> prepareAttrs(
            final Any<?> any,
            final String password,
            final boolean changePwd,
            final Boolean enable,
            final Provision provision) {

        LOG.debug("Preparing resource attributes for {} with provision {} for attributes {}",
                any, provision, any.getPlainAttrs());

        Set<Attribute> attributes = new HashSet<>();
        String connObjectKey = null;

        for (Item mapItem : MappingUtils.getPropagationItems(provision.getMapping().getItems())) {
            LOG.debug("Processing expression '{}'", mapItem.getIntAttrName());

            try {
                String processedConnObjectKey = processPreparedAttr(
                        prepareAttr(
                                provision,
                                mapItem,
                                any,
                                password,
                                AccountGetter.DEFAULT,
                                AccountGetter.DEFAULT,
                                PlainAttrGetter.DEFAULT),
                        attributes);
                if (processedConnObjectKey != null) {
                    connObjectKey = processedConnObjectKey;
                }
            } catch (Exception e) {
                LOG.error("Expression '{}' processing failed", mapItem.getIntAttrName(), e);
            }
        }

        Optional<? extends MappingItem> connObjectKeyItem = MappingUtils.getConnObjectKeyItem(provision);
        if (connObjectKeyItem.isPresent()) {
            Attribute connObjectKeyExtAttr = AttributeUtil.find(connObjectKeyItem.get().getExtAttrName(), attributes);
            if (connObjectKeyExtAttr != null) {
                attributes.remove(connObjectKeyExtAttr);
                attributes.add(AttributeBuilder.build(connObjectKeyItem.get().getExtAttrName(), connObjectKey));
            }
            Name name = MappingUtils.evaluateNAME(any, provision, connObjectKey);
            attributes.add(name);
            if (connObjectKey != null && !connObjectKey.equals(name.getNameValue()) && connObjectKeyExtAttr == null) {
                attributes.add(AttributeBuilder.build(connObjectKeyItem.get().getExtAttrName(), connObjectKey));
            }
        }

        if (enable != null) {
            attributes.add(AttributeBuilder.buildEnabled(enable));
        }
        if (!changePwd) {
            Attribute pwdAttr = AttributeUtil.find(OperationalAttributes.PASSWORD_NAME, attributes);
            if (pwdAttr != null) {
                attributes.remove(pwdAttr);
            }
        }

        return Pair.of(connObjectKey, attributes);
    }

    @Transactional(readOnly = true)
    @Override
    public Set<Attribute> prepareAttrs(
            final User user,
            final LinkedAccount account,
            final String password,
            final boolean changePwd,
            final Provision provision) {

        LOG.debug("Preparing resource attributes for linked account {} of user {} with provision {} "
                + "for user attributes {} with override {}",
                account, user, provision, user.getPlainAttrs(), account.getPlainAttrs());

        Set<Attribute> attributes = new HashSet<>();

        for (Item mapItem : MappingUtils.getPropagationItems(provision.getMapping().getItems())) {
            LOG.debug("Processing expression '{}'", mapItem.getIntAttrName());

            try {
                processPreparedAttr(
                        prepareAttr(
                                provision,
                                mapItem,
                                user,
                                password,
                                acct -> account.getUsername() == null ? AccountGetter.DEFAULT.apply(acct) : account,
                                acct -> account.getPassword() == null ? AccountGetter.DEFAULT.apply(acct) : account,
                                (attributable, schema) -> {
                                    PlainAttr<?> result = null;
                                    if (attributable instanceof User) {
                                        Optional<? extends LAPlainAttr> accountAttr = account.getPlainAttr(schema);
                                        if (accountAttr.isPresent()) {
                                            result = accountAttr.get();
                                        }
                                    }
                                    if (result == null) {
                                        result = PlainAttrGetter.DEFAULT.apply(attributable, schema);
                                    }
                                    return result;
                                }),
                        attributes);
            } catch (Exception e) {
                LOG.error("Expression '{}' processing failed", mapItem.getIntAttrName(), e);
            }
        }

        String connObjectKey = account.getConnObjectKeyValue();
        MappingUtils.getConnObjectKeyItem(provision).ifPresent(connObjectKeyItem -> {
            Attribute connObjectKeyExtAttr = AttributeUtil.find(connObjectKeyItem.getExtAttrName(), attributes);
            if (connObjectKeyExtAttr != null) {
                attributes.remove(connObjectKeyExtAttr);
                attributes.add(AttributeBuilder.build(connObjectKeyItem.getExtAttrName(), connObjectKey));
            }
            Name name = MappingUtils.evaluateNAME(user, provision, connObjectKey);
            attributes.add(name);
            if (!connObjectKey.equals(name.getNameValue()) && connObjectKeyExtAttr == null) {
                attributes.add(AttributeBuilder.build(connObjectKeyItem.getExtAttrName(), connObjectKey));
            }
        });

        if (account.isSuspended() != null) {
            attributes.add(AttributeBuilder.buildEnabled(!BooleanUtils.negate(account.isSuspended())));
        }

        return attributes;
    }

    private String getIntValue(final Realm realm, final Item orgUnitItem) {
        String value = null;
        switch (orgUnitItem.getIntAttrName()) {
            case "key":
                value = realm.getKey();
                break;

            case "name":
                value = realm.getName();
                break;

            case "fullpath":
                value = realm.getFullPath();
                break;

            default:
        }

        return value;
    }

    @Override
    public Pair<String, Set<Attribute>> prepareAttrs(final Realm realm, final OrgUnit orgUnit) {
        LOG.debug("Preparing resource attributes for {} with orgUnit {}", realm, orgUnit);

        Set<Attribute> attributes = new HashSet<>();
        String connObjectKey = null;

        for (Item orgUnitItem : MappingUtils.getPropagationItems(orgUnit.getItems())) {
            LOG.debug("Processing expression '{}'", orgUnitItem.getIntAttrName());

            String value = getIntValue(realm, orgUnitItem);

            if (orgUnitItem.isConnObjectKey()) {
                connObjectKey = value;
            }

            Attribute alreadyAdded = AttributeUtil.find(orgUnitItem.getExtAttrName(), attributes);
            if (alreadyAdded == null) {
                if (value == null) {
                    attributes.add(AttributeBuilder.build(orgUnitItem.getExtAttrName()));
                } else {
                    attributes.add(AttributeBuilder.build(orgUnitItem.getExtAttrName(), value));
                }
            } else if (value != null) {
                attributes.remove(alreadyAdded);

                Set<Object> values = new HashSet<>();
                if (alreadyAdded.getValue() != null && !alreadyAdded.getValue().isEmpty()) {
                    values.addAll(alreadyAdded.getValue());
                }
                values.add(value);

                attributes.add(AttributeBuilder.build(orgUnitItem.getExtAttrName(), values));
            }
        }

        Optional<? extends OrgUnitItem> connObjectKeyItem = orgUnit.getConnObjectKeyItem();
        if (connObjectKeyItem.isPresent()) {
            Attribute connObjectKeyExtAttr = AttributeUtil.find(connObjectKeyItem.get().getExtAttrName(), attributes);
            if (connObjectKeyExtAttr != null) {
                attributes.remove(connObjectKeyExtAttr);
                attributes.add(AttributeBuilder.build(connObjectKeyItem.get().getExtAttrName(), connObjectKey));
            }
            attributes.add(MappingUtils.evaluateNAME(realm, orgUnit, connObjectKey));
        }

        return Pair.of(connObjectKey, attributes);
    }

    protected String getPasswordAttrValue(final Provision provision, final Account account, final String defaultValue) {
        String passwordAttrValue = defaultValue;
        if (StringUtils.isBlank(passwordAttrValue)) {
            if (account.canDecodePassword()) {
                try {
                    passwordAttrValue = ENCRYPTOR.decode(account.getPassword(), account.getCipherAlgorithm());
                } catch (Exception e) {
                    LOG.error("Could not decode password for {}", account, e);
                }
            } else if (provision.getResource().isRandomPwdIfNotProvided()) {
                try {
                    passwordAttrValue = passwordGenerator.generate(provision.getResource());
                } catch (InvalidPasswordRuleConf e) {
                    LOG.error("Could not generate policy-compliant random password for {}", account, e);
                }
            }
        }

        return passwordAttrValue;
    }

    @Override
    public Pair<String, Attribute> prepareAttr(
            final Provision provision,
            final Item item,
            final Any<?> any,
            final String password,
            final AccountGetter usernameAccountGetter,
            final AccountGetter passwordAccountGetter,
            final PlainAttrGetter plainAttrGetter) {

        IntAttrName intAttrName;
        try {
            intAttrName = intAttrNameParser.parse(item.getIntAttrName(), provision.getAnyType().getKind());
        } catch (ParseException e) {
            LOG.error("Invalid intAttrName '{}' specified, ignoring", item.getIntAttrName(), e);
            return null;
        }

        AttrSchemaType schemaType = intAttrName.getSchema() instanceof PlainSchema
                ? ((PlainSchema) intAttrName.getSchema()).getType()
                : AttrSchemaType.String;
        boolean readOnlyVirSchema = intAttrName.getSchema() instanceof VirSchema
                ? intAttrName.getSchema().isReadonly()
                : false;

        Pair<AttrSchemaType, List<PlainAttrValue>> intValues =
                getIntValues(provision, item, intAttrName, schemaType, any, usernameAccountGetter, plainAttrGetter);
        schemaType = intValues.getLeft();
        List<PlainAttrValue> values = intValues.getRight();

        LOG.debug("Define mapping for: "
                + "\n* ExtAttrName " + item.getExtAttrName()
                + "\n* is connObjectKey " + item.isConnObjectKey()
                + "\n* is password " + item.isPassword()
                + "\n* mandatory condition " + item.getMandatoryCondition()
                + "\n* Schema " + intAttrName.getSchema()
                + "\n* ClassType " + schemaType.getType().getName()
                + "\n* AttrSchemaType " + schemaType
                + "\n* Values " + values);

        Pair<String, Attribute> result;
        if (readOnlyVirSchema) {
            result = null;
        } else {
            List<Object> objValues = new ArrayList<>();

            for (PlainAttrValue value : values) {
                if (FrameworkUtil.isSupportedAttributeType(schemaType.getType())) {
                    objValues.add(value.getValue());
                } else {
                    PlainSchema plainSchema = intAttrName.getSchema() instanceof PlainSchema
                            ? (PlainSchema) intAttrName.getSchema()
                            : null;
                    if (plainSchema == null || plainSchema.getType() != schemaType) {
                        objValues.add(value.getValueAsString(schemaType));
                    } else {
                        objValues.add(value.getValueAsString(plainSchema));
                    }
                }
            }

            if (item.isConnObjectKey()) {
                result = Pair.of(objValues.isEmpty() ? null : objValues.iterator().next().toString(), null);
            } else if (item.isPassword() && any instanceof User) {
                String passwordAttrValue =
                        getPasswordAttrValue(provision, passwordAccountGetter.apply((User) any), password);
                if (passwordAttrValue == null) {
                    result = null;
                } else {
                    result = Pair.of(null, AttributeBuilder.buildPassword(passwordAttrValue.toCharArray()));
                }
            } else {
                result = Pair.of(null, objValues.isEmpty()
                        ? AttributeBuilder.build(item.getExtAttrName())
                        : AttributeBuilder.build(item.getExtAttrName(), objValues));
            }
        }

        return result;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    @Override
    public Pair<AttrSchemaType, List<PlainAttrValue>> getIntValues(
            final Provision provision,
            final Item mapItem,
            final IntAttrName intAttrName,
            final AttrSchemaType schemaType,
            final Any<?> any,
            final AccountGetter usernameAccountGetter,
            final PlainAttrGetter plainAttrGetter) {

        LOG.debug("Get internal values for {} as '{}' on {}", any, mapItem.getIntAttrName(), provision.getResource());

        List<Any<?>> references = new ArrayList<>();
        Membership<?> membership = null;
        if (intAttrName.getEnclosingGroup() == null
                && intAttrName.getRelatedAnyObject() == null
                && intAttrName.getRelationshipAnyType() == null
                && intAttrName.getRelationshipType() == null
                && intAttrName.getRelatedUser() == null) {
            references.add(any);
        }
        if (any instanceof GroupableRelatable) {
            GroupableRelatable<?, ?, ?, ?, ?> groupableRelatable = (GroupableRelatable<?, ?, ?, ?, ?>) any;

            if (intAttrName.getEnclosingGroup() != null) {
                Group group = groupDAO.findByName(intAttrName.getEnclosingGroup());
                if (group == null || groupableRelatable.getMembership(group.getKey()).isEmpty()) {
                    LOG.warn("No membership for {} in {}, ignoring",
                            intAttrName.getEnclosingGroup(), groupableRelatable);
                } else {
                    references.add(group);
                }
            } else if (intAttrName.getRelatedUser() != null) {
                User user = userDAO.findByUsername(intAttrName.getRelatedUser());
                if (user == null || user.getRelationships(groupableRelatable.getKey()).isEmpty()) {
                    LOG.warn("No relationship for {} in {}, ignoring",
                            intAttrName.getRelatedUser(), groupableRelatable);
                } else if (groupableRelatable.getType().getKind() == AnyTypeKind.USER) {
                    LOG.warn("Users cannot have relationship with other users, ignoring");
                } else {
                    references.add(user);
                }
            } else if (intAttrName.getRelatedAnyObject() != null) {
                AnyObject anyObject = anyObjectDAO.findByName(intAttrName.getRelatedAnyObject());
                if (anyObject == null || groupableRelatable.getRelationships(anyObject.getKey()).isEmpty()) {
                    LOG.warn("No relationship for {} in {}, ignoring",
                            intAttrName.getRelatedAnyObject(), groupableRelatable);
                } else {
                    references.add(anyObject);
                }
            } else if (intAttrName.getRelationshipAnyType() != null && intAttrName.getRelationshipType() != null) {
                RelationshipType relationshipType = relationshipTypeDAO.find(intAttrName.getRelationshipType());
                final AnyType anyType = anyTypeDAO.find(intAttrName.getRelationshipAnyType());
                if (relationshipType == null || groupableRelatable.getRelationships(relationshipType).isEmpty()) {
                    LOG.warn("No relationship for type {} in {}, ignoring",
                            intAttrName.getRelationshipType(), groupableRelatable);
                } else if (anyType == null) {
                    LOG.warn("No anyType {}, ignoring", intAttrName.getRelationshipAnyType());
                } else {
                    references.addAll(groupableRelatable.getRelationships(relationshipType).stream().
                            filter(relationship -> anyType.equals(relationship.getRightEnd().getType())).
                            map(Relationship::getRightEnd).
                            collect(Collectors.toList()));
                }
            } else if (intAttrName.getMembershipOfGroup() != null) {
                Group group = groupDAO.findByName(intAttrName.getMembershipOfGroup());
                membership = groupableRelatable.getMembership(group.getKey()).orElse(null);
            }
        }
        if (references.isEmpty()) {
            LOG.warn("Could not determine the reference instance for {}", mapItem.getIntAttrName());
            return Pair.of(schemaType, List.of());
        }

        List<PlainAttrValue> values = new ArrayList<>();
        boolean transform = true;

        for (Any<?> ref : references) {
            AnyUtils anyUtils = anyUtilsFactory.getInstance(ref);
            if (intAttrName.getField() != null) {
                PlainAttrValue attrValue = anyUtils.newPlainAttrValue();

                switch (intAttrName.getField()) {
                    case "key":
                        attrValue.setStringValue(ref.getKey());
                        values.add(attrValue);
                        break;

                    case "username":
                        if (ref instanceof Account) {
                            attrValue.setStringValue(usernameAccountGetter.apply((Account) ref).getUsername());
                            values.add(attrValue);
                        }
                        break;

                    case "realm":
                        attrValue.setStringValue(ref.getRealm().getFullPath());
                        values.add(attrValue);
                        break;

                    case "password":
                        // ignore
                        break;

                    case "userOwner":
                    case "groupOwner":
                        Mapping uMapping = provision.getAnyType().equals(anyTypeDAO.findUser())
                                ? provision.getMapping()
                                : null;
                        Mapping gMapping = provision.getAnyType().equals(anyTypeDAO.findGroup())
                                ? provision.getMapping()
                                : null;

                        if (ref instanceof Group) {
                            Group group = (Group) ref;
                            String groupOwnerValue = null;
                            if (group.getUserOwner() != null && uMapping != null) {
                                groupOwnerValue = getGroupOwnerValue(provision, group.getUserOwner());
                            }
                            if (group.getGroupOwner() != null && gMapping != null) {
                                groupOwnerValue = getGroupOwnerValue(provision, group.getGroupOwner());
                            }

                            if (StringUtils.isNotBlank(groupOwnerValue)) {
                                attrValue.setStringValue(groupOwnerValue);
                                values.add(attrValue);
                            }
                        }
                        break;

                    case "suspended":
                        if (ref instanceof User) {
                            attrValue.setBooleanValue(((User) ref).isSuspended());
                            values.add(attrValue);
                        }
                        break;

                    case "mustChangePassword":
                        if (ref instanceof User) {
                            attrValue.setBooleanValue(((User) ref).isMustChangePassword());
                            values.add(attrValue);
                        }
                        break;

                    default:
                        try {
                            Object fieldValue = FieldUtils.readField(ref, intAttrName.getField(), true);
                            if (fieldValue instanceof Date) {
                                // needed because ConnId does not natively supports the Date type
                                attrValue.setStringValue(DateFormatUtils.ISO_8601_EXTENDED_DATETIME_TIME_ZONE_FORMAT.
                                        format((Date) fieldValue));
                            } else if (Boolean.TYPE.isInstance(fieldValue)) {
                                attrValue.setBooleanValue((Boolean) fieldValue);
                            } else if (Double.TYPE.isInstance(fieldValue) || Float.TYPE.isInstance(fieldValue)) {
                                attrValue.setDoubleValue((Double) fieldValue);
                            } else if (Long.TYPE.isInstance(fieldValue) || Integer.TYPE.isInstance(fieldValue)) {
                                attrValue.setLongValue((Long) fieldValue);
                            } else {
                                attrValue.setStringValue(fieldValue.toString());
                            }
                            values.add(attrValue);
                        } catch (Exception e) {
                            LOG.error("Could not read value of '{}' from {}", intAttrName.getField(), ref, e);
                        }
                }
            } else if (intAttrName.getSchemaType() != null) {
                switch (intAttrName.getSchemaType()) {
                    case PLAIN:
                        PlainAttr<?> attr;
                        if (membership == null) {
                            if (ref instanceof Attributable) {
                                attr = plainAttrGetter.apply((Attributable) ref, intAttrName.getSchema().getKey());
                            } else {
                                attr = ref.getPlainAttr(intAttrName.getSchema().getKey()).orElse(null);
                            }
                        } else {
                            attr = ((GroupableRelatable<?, ?, ?, ?, ?>) ref).getPlainAttr(
                                    intAttrName.getSchema().getKey(), membership).orElse(null);
                        }
                        if (attr == null) {
                            LOG.warn("Invalid PlainSchema {} or PlainAttr not found for {}",
                                    intAttrName.getSchema().getKey(), ref);
                        } else {
                            if (attr.getUniqueValue() != null) {
                                values.add(anyUtils.clonePlainAttrValue(attr.getUniqueValue()));
                            } else if (attr.getValues() != null) {
                                attr.getValues().forEach(value -> values.add(anyUtils.clonePlainAttrValue(value)));
                            }
                        }
                        break;

                    case DERIVED:
                        DerSchema derSchema = (DerSchema) intAttrName.getSchema();
                        String derValue = membership == null
                                ? derAttrHandler.getValue(ref, derSchema)
                                : derAttrHandler.getValue(ref, membership, derSchema);
                        if (derValue != null) {
                            PlainAttrValue attrValue = anyUtils.newPlainAttrValue();
                            attrValue.setStringValue(derValue);
                            values.add(attrValue);
                        }
                        break;

                    case VIRTUAL:
                        // virtual attributes don't get transformed
                        transform = false;

                        VirSchema virSchema = (VirSchema) intAttrName.getSchema();
                        LOG.debug("Expire entry cache {}-{}", ref, intAttrName.getSchema().getKey());
                        virAttrCache.expire(
                                ref.getType().getKey(), ref.getKey(), intAttrName.getSchema().getKey());

                        List<String> virValues = membership == null
                                ? virAttrHandler.getValues(ref, virSchema)
                                : virAttrHandler.getValues(ref, membership, virSchema);
                        virValues.forEach(virValue -> {
                            PlainAttrValue attrValue = anyUtils.newPlainAttrValue();
                            attrValue.setStringValue(virValue);
                            values.add(attrValue);
                        });
                        break;

                    default:
                }
            } else if (intAttrName.getPrivilegesOfApplication() != null && ref instanceof User) {
                Application application = applicationDAO.find(intAttrName.getPrivilegesOfApplication());
                if (application == null) {
                    LOG.warn("Invalid application: {}", intAttrName.getPrivilegesOfApplication());
                } else {
                    userDAO.findAllRoles((User) ref).stream().
                            flatMap(role -> role.getPrivileges(application).stream()).
                            forEach(privilege -> {
                                PlainAttrValue attrValue = anyUtils.newPlainAttrValue();
                                attrValue.setStringValue(privilege.getKey());
                                values.add(attrValue);
                            });
                }
            }
        }

        LOG.debug("Internal values: {}", values);

        Pair<AttrSchemaType, List<PlainAttrValue>> trans = Pair.of(schemaType, values);
        if (transform) {
            for (ItemTransformer transformer : MappingUtils.getItemTransformers(mapItem)) {
                trans = transformer.beforePropagation(mapItem, any, trans.getLeft(), trans.getRight());
            }
            LOG.debug("Transformed values: {}", values);
        } else {
            LOG.debug("No transformation occurred");
        }

        return trans;
    }

    private String getGroupOwnerValue(final Provision provision, final Any<?> any) {
        Optional<? extends MappingItem> connObjectKeyItem = MappingUtils.getConnObjectKeyItem(provision);

        Pair<String, Attribute> preparedAttr = null;
        if (connObjectKeyItem.isPresent()) {
            preparedAttr = prepareAttr(
                    provision,
                    connObjectKeyItem.get(),
                    any,
                    null,
                    AccountGetter.DEFAULT,
                    AccountGetter.DEFAULT,
                    PlainAttrGetter.DEFAULT);
        }

        return Optional.ofNullable(preparedAttr)
                .map(attr -> MappingUtils.evaluateNAME(any, provision, attr.getKey()).getNameValue()).orElse(null);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<String> getConnObjectKeyValue(final Any<?> any, final Provision provision) {
        MappingItem mapItem = provision.getMapping().getConnObjectKeyItem().get();
        Pair<AttrSchemaType, List<PlainAttrValue>> intValues;
        try {
            intValues = getIntValues(provision,
                    mapItem,
                    intAttrNameParser.parse(mapItem.getIntAttrName(), provision.getAnyType().getKind()),
                    AttrSchemaType.String,
                    any,
                    AccountGetter.DEFAULT,
                    PlainAttrGetter.DEFAULT);
        } catch (ParseException e) {
            LOG.error("Invalid intAttrName '{}' specified, ignoring", mapItem.getIntAttrName(), e);
            intValues = Pair.of(AttrSchemaType.String, List.of());
        }
        return Optional.ofNullable(intValues.getRight().isEmpty()
                ? null
                : intValues.getRight().get(0).getValueAsString());
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<String> getConnObjectKeyValue(final Realm realm, final OrgUnit orgUnit) {
        OrgUnitItem orgUnitItem = orgUnit.getConnObjectKeyItem().get();

        return Optional.ofNullable(Optional.of(orgUnitItem)
                .map(unitItem -> getIntValue(realm, unitItem)).orElse(null));
    }

    @Transactional(readOnly = true)
    @Override
    public void setIntValues(final Item mapItem, final Attribute attr, final AnyTO anyTO) {
        List<Object> values = null;
        if (attr != null) {
            values = attr.getValue();
            for (ItemTransformer transformer : MappingUtils.getItemTransformers(mapItem)) {
                values = transformer.beforePull(mapItem, anyTO, values);
            }
        }
        values = Optional.ofNullable(values).orElse(List.of());

        IntAttrName intAttrName;
        try {
            intAttrName = intAttrNameParser.parse(mapItem.getIntAttrName(), AnyTypeKind.fromTOClass(anyTO.getClass()));
        } catch (ParseException e) {
            LOG.error("Invalid intAttrName '{}' specified, ignoring", mapItem.getIntAttrName(), e);
            return;
        }

        if (intAttrName.getField() != null) {
            switch (intAttrName.getField()) {
                case "password":
                    if (anyTO instanceof UserTO && !values.isEmpty()) {
                        ((UserTO) anyTO).setPassword(ConnObjectUtils.getPassword(values.get(0)));
                    }
                    break;

                case "username":
                    if (anyTO instanceof UserTO) {
                        ((UserTO) anyTO).setUsername(values.isEmpty() || values.get(0) == null
                                ? null
                                : values.get(0).toString());
                    }
                    break;

                case "name":
                    if (anyTO instanceof GroupTO) {
                        ((GroupTO) anyTO).setName(values.isEmpty() || values.get(0) == null
                                ? null
                                : values.get(0).toString());
                    } else if (anyTO instanceof AnyObjectTO) {
                        ((AnyObjectTO) anyTO).setName(values.isEmpty() || values.get(0) == null
                                ? null
                                : values.get(0).toString());
                    }
                    break;

                case "mustChangePassword":
                    if (anyTO instanceof UserTO && !values.isEmpty() && values.get(0) != null) {
                        ((UserTO) anyTO).setMustChangePassword(BooleanUtils.toBoolean(values.get(0).toString()));
                    }
                    break;

                case "userOwner":
                case "groupOwner":
                    if (anyTO instanceof GroupTO && attr != null) {
                        // using a special attribute (with schema "", that will be ignored) for carrying the
                        // GroupOwnerSchema value
                        Attr attrTO = new Attr();
                        attrTO.setSchema(StringUtils.EMPTY);
                        if (values.isEmpty() || values.get(0) == null) {
                            attrTO.getValues().add(StringUtils.EMPTY);
                        } else {
                            attrTO.getValues().add(values.get(0).toString());
                        }

                        ((GroupTO) anyTO).getPlainAttrs().add(attrTO);
                    }
                    break;

                default:
            }
        } else if (intAttrName.getSchemaType() != null && attr != null) {
            GroupableRelatableTO groupableTO;
            Group group;
            if (anyTO instanceof GroupableRelatableTO && intAttrName.getMembershipOfGroup() != null) {
                groupableTO = (GroupableRelatableTO) anyTO;
                group = groupDAO.findByName(intAttrName.getMembershipOfGroup());
            } else {
                groupableTO = null;
                group = null;
            }

            switch (intAttrName.getSchemaType()) {
                case PLAIN:
                    Attr attrTO = new Attr();
                    attrTO.setSchema(intAttrName.getSchema().getKey());

                    PlainSchema schema = (PlainSchema) intAttrName.getSchema();

                    for (Object value : values) {
                        AttrSchemaType schemaType = schema == null ? AttrSchemaType.String : schema.getType();
                        if (value != null) {
                            if (schemaType == AttrSchemaType.Binary) {
                                attrTO.getValues().add(Base64.getEncoder().encodeToString((byte[]) value));
                            } else {
                                attrTO.getValues().add(value.toString());
                            }
                        }
                    }

                    if (groupableTO == null || group == null) {
                        anyTO.getPlainAttrs().add(attrTO);
                    } else {
                        MembershipTO membership = groupableTO.getMembership(group.getKey()).orElseGet(() -> {
                            MembershipTO newMemb = new MembershipTO.Builder(group.getKey()).build();
                            groupableTO.getMemberships().add(newMemb);
                            return newMemb;
                        });
                        membership.getPlainAttrs().add(attrTO);
                    }
                    break;

                case DERIVED:
                    attrTO = new Attr();
                    attrTO.setSchema(intAttrName.getSchema().getKey());

                    if (groupableTO == null || group == null) {
                        anyTO.getDerAttrs().add(attrTO);
                    } else {
                        MembershipTO membership = groupableTO.getMembership(group.getKey()).orElseGet(() -> {
                            MembershipTO newMemb = new MembershipTO.Builder(group.getKey()).build();
                            groupableTO.getMemberships().add(newMemb);
                            return newMemb;
                        });
                        membership.getDerAttrs().add(attrTO);
                    }
                    break;

                case VIRTUAL:
                    attrTO = new Attr();
                    attrTO.setSchema(intAttrName.getSchema().getKey());

                    // virtual attributes don't get transformed, iterate over original attr.getValue()
                    if (attr.getValue() != null && !attr.getValue().isEmpty()) {
                        attr.getValue().stream().
                                filter(Objects::nonNull).
                                forEachOrdered(value -> attrTO.getValues().add(value.toString()));
                    }

                    if (groupableTO == null || group == null) {
                        anyTO.getVirAttrs().add(attrTO);
                    } else {
                        MembershipTO membership = groupableTO.getMembership(group.getKey()).orElseGet(() -> {
                            MembershipTO newMemb = new MembershipTO.Builder(group.getKey()).build();
                            groupableTO.getMemberships().add(newMemb);
                            return newMemb;
                        });
                        membership.getVirAttrs().add(attrTO);
                    }
                    break;

                default:
            }
        }
    }

    @Override
    public void setIntValues(final Item orgUnitItem, final Attribute attr, final RealmTO realmTO) {
        List<Object> values = null;
        if (attr != null) {
            values = attr.getValue();
            for (ItemTransformer transformer : MappingUtils.getItemTransformers(orgUnitItem)) {
                values = transformer.beforePull(orgUnitItem, realmTO, values);
            }
        }

        if (values != null && !values.isEmpty() && values.get(0) != null) {
            switch (orgUnitItem.getIntAttrName()) {
                case "name":
                    realmTO.setName(values.get(0).toString());
                    break;

                case "fullpath":
                    String parentFullPath = StringUtils.substringBeforeLast(values.get(0).toString(), "/");
                    Realm parent = realmDAO.findByFullPath(parentFullPath);
                    if (parent == null) {
                        LOG.warn("Could not find Realm with path {}, ignoring", parentFullPath);
                    } else {
                        realmTO.setParent(parent.getFullPath());
                    }
                    break;

                default:
            }
        }
    }

    @Transactional(readOnly = true)
    @Override
    public boolean hasMustChangePassword(final Provision provision) {
        return provision != null && provision.getMapping() != null
                && provision.getMapping().getItems().stream().
                        anyMatch(mappingItem -> "mustChangePassword".equals(mappingItem.getIntAttrName()));
    }
}
