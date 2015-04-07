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
package org.apache.syncope.core.persistence.jpa.entity.task;

import java.util.HashSet;
import java.util.Set;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.syncope.common.lib.types.AttributableType;
import org.apache.syncope.common.lib.types.PropagationMode;
import org.apache.syncope.common.lib.types.ResourceOperation;
import org.apache.syncope.common.lib.types.TaskType;
import org.apache.syncope.core.persistence.api.entity.ExternalResource;
import org.apache.syncope.core.persistence.api.entity.task.PropagationTask;
import org.apache.syncope.core.persistence.jpa.validation.entity.PropagationTaskCheck;
import org.apache.syncope.core.persistence.jpa.entity.JPAExternalResource;
import org.apache.syncope.core.misc.serialization.POJOHelper;
import org.identityconnectors.framework.common.objects.Attribute;

/**
 * Encapsulate all information about a propagation task.
 */
@Entity
@DiscriminatorValue("PropagationTask")
@PropagationTaskCheck
public class JPAPropagationTask extends JPATask implements PropagationTask {

    private static final long serialVersionUID = 7086054884614511210L;

    /**
     * @see PropagationMode
     */
    @Enumerated(EnumType.STRING)
    private PropagationMode propagationMode;

    /**
     * @see ResourceOperation
     */
    @Enumerated(EnumType.STRING)
    private ResourceOperation propagationOperation;

    /**
     * The accountId on the external resource.
     */
    private String accountId;

    /**
     * The (optional) former accountId on the external resource.
     */
    private String oldAccountId;

    /**
     * Attributes to be propagated.
     */
    @Lob
    private String xmlAttributes;

    private String objectClassName;

    @Enumerated(EnumType.STRING)
    private AttributableType subjectType;

    private Long subjectId;

    public JPAPropagationTask() {
        super();
        type = TaskType.PROPAGATION;
    }

    /**
     * ExternalResource to which the propagation happens.
     */
    @ManyToOne
    private JPAExternalResource resource;

    @Override
    public String getAccountId() {
        return accountId;
    }

    @Override
    public void setAccountId(final String accountId) {
        this.accountId = accountId;
    }

    @Override
    public String getOldAccountId() {
        return oldAccountId;
    }

    @Override
    public void setOldAccountId(final String oldAccountId) {
        this.oldAccountId = oldAccountId;
    }

    @Override
    public Set<Attribute> getAttributes() {
        Set<Attribute> attributes = new HashSet<>();
        if (!StringUtils.isBlank(xmlAttributes)) {
            CollectionUtils.addAll(attributes, POJOHelper.deserialize(xmlAttributes, Attribute[].class));
        }

        return attributes;
    }

    @Override
    public void setAttributes(final Set<Attribute> attributes) {
        xmlAttributes = POJOHelper.serialize(attributes);
    }

    @Override

    public PropagationMode getPropagationMode() {
        return propagationMode;
    }

    @Override

    public void setPropagationMode(final PropagationMode propagationMode) {
        this.propagationMode = propagationMode;
    }

    @Override

    public ResourceOperation getPropagationOperation() {
        return propagationOperation;
    }

    @Override

    public void setPropagationOperation(final ResourceOperation propagationOperation) {
        this.propagationOperation = propagationOperation;
    }

    @Override
    public ExternalResource getResource() {
        return resource;
    }

    @Override
    public void setResource(final ExternalResource resource) {
        checkType(resource, JPAExternalResource.class);
        this.resource = (JPAExternalResource) resource;
    }

    @Override
    public String getObjectClassName() {
        return objectClassName;
    }

    @Override
    public void setObjectClassName(final String objectClassName) {
        this.objectClassName = objectClassName;
    }

    @Override
    public AttributableType getSubjectType() {
        return subjectType;
    }

    @Override
    public void setSubjectType(final AttributableType subjectType) {
        this.subjectType = subjectType;
    }

    @Override
    public Long getSubjectKey() {
        return subjectId;
    }

    @Override
    public void setSubjectKey(final Long subjectKey) {
        this.subjectId = subjectKey;
    }
}
