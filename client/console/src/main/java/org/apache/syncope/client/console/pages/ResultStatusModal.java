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
package org.apache.syncope.client.console.pages;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.syncope.client.console.commons.ConnIdSpecialAttributeName;
import org.apache.syncope.client.console.commons.Constants;
import org.apache.syncope.client.console.commons.Mode;
import org.apache.syncope.client.console.commons.status.Status;
import org.apache.syncope.client.console.commons.status.StatusUtils;
import org.apache.syncope.client.console.panels.AbstractModalPanel;
import org.apache.syncope.client.console.panels.FailureMessageModal;
import org.apache.syncope.client.console.rest.UserRestClient;
import org.apache.syncope.client.console.wicket.markup.html.bootstrap.dialog.BaseModal;
import org.apache.syncope.common.lib.to.AnyTO;
import org.apache.syncope.common.lib.to.AttrTO;
import org.apache.syncope.common.lib.to.ConnObjectTO;
import org.apache.syncope.common.lib.to.PropagationStatus;
import org.apache.syncope.common.lib.to.GroupTO;
import org.apache.syncope.common.lib.to.ProvisioningResult;
import org.apache.syncope.common.lib.to.UserTO;
import org.apache.syncope.common.lib.types.PropagationTaskExecStatus;
import org.apache.wicket.Component;
import org.apache.wicket.PageReference;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.extensions.ajax.markup.html.IndicatingAjaxLink;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.Image;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.ResourceModel;
import org.apache.wicket.request.resource.ContextRelativeResource;

/**
 * Show user or group status after performing a successful operation.
 *
 * @param <T>
 */
public final class ResultStatusModal<T extends AnyTO> extends AbstractModalPanel {

    private static final long serialVersionUID = 2646115294319713723L;

    private static final String IMG_PREFIX = "/img/statuses/";

    private final ProvisioningResult<AnyTO> provResult;

    private final Mode mode;

    /**
     * Status management utilities.
     */
    private final StatusUtils statusUtils;

    public static class Builder<T extends AnyTO> implements Serializable {

        private static final long serialVersionUID = 220361441802274899L;

        private Mode mode;

        private ProvisioningResult<AnyTO> provResult;

        private final BaseModal<T> modal;

        private final PageReference pageRef;

        public Builder(
                final BaseModal<T> modal,
                final PageReference pageRef,
                final ProvisioningResult<AnyTO> provResult) {
            this.provResult = provResult;
            this.modal = modal;
            this.pageRef = pageRef;
        }

        public ResultStatusModal.Builder<T> mode(final Mode mode) {
            this.mode = mode;
            return this;
        }

        public ResultStatusModal<T> build() {
            return new ResultStatusModal<>(modal, pageRef, this);
        }
    }

    private ResultStatusModal(
            final BaseModal<T> modal,
            final PageReference pageRef,
            final Builder<T> builder) {

        super(modal, pageRef);

        this.provResult = builder.provResult;
        statusUtils = new StatusUtils(new UserRestClient());
        if (builder.mode == null) {
            this.mode = Mode.ADMIN;
        } else {
            this.mode = builder.mode;
        }

        final WebMarkupContainer container = new WebMarkupContainer("container");
        container.setOutputMarkupId(true);
        add(container);

        final Fragment fragment = new Fragment("resultFrag", mode == Mode.SELF
                ? "userSelfResultFrag"
                : "propagationResultFrag", this);
        fragment.setOutputMarkupId(true);
        container.add(fragment);

        if (mode == Mode.ADMIN) {
            // add Syncope propagation status
            PropagationStatus syncope = new PropagationStatus();
            syncope.setResource("Syncope");
            syncope.setStatus(PropagationTaskExecStatus.SUCCESS);

            List<PropagationStatus> propagations = new ArrayList<PropagationStatus>();
            propagations.add(syncope);
            propagations.addAll(provResult.getPropagationStatuses());

            AnyTO any = provResult.getAny();

            fragment.add(new Label("info",
                    ((any instanceof UserTO) && ((UserTO) any).getUsername() != null)
                            ? ((UserTO) any).getUsername()
                            : ((any instanceof GroupTO) && ((GroupTO) any).getName() != null)
                                    ? ((GroupTO) any).getName()
                                    : String.valueOf(any.getKey())));

            final ListView<PropagationStatus> propRes = new ListView<PropagationStatus>("resources",
                    propagations) {

                        private static final long serialVersionUID = -1020475259727720708L;

                        @Override
                        protected void populateItem(final ListItem<PropagationStatus> item) {
                            final PropagationStatus propTO = (PropagationStatus) item.getDefaultModelObject();

                            final ListView<String> attributes = getConnObjectView(propTO);

                            final Fragment attrhead;
                            if (attributes.getModelObject() == null || attributes.getModelObject().isEmpty()) {
                                attrhead = new Fragment("attrhead", "emptyAttrHeadFrag", this);
                            } else {
                                attrhead = new Fragment("attrhead", "attrHeadFrag", this);
                            }

                            item.add(attrhead);
                            item.add(attributes);

                            attrhead.add(new Label("resource", propTO.getResource()));

                            attrhead.add(new Label("propagation", propTO.getStatus() == null
                                                    ? "UNDEFINED" : propTO.getStatus().toString()));

                            final Image image;
                            final String alt, title;

                            final BaseModal<?> failureWindow = new BaseModal<>("failureWindow");

                            final AjaxLink<?> failureWindowLink = new AjaxLink<Void>("showFailureWindow") {

                                private static final long serialVersionUID = -7978723352517770644L;

                                @Override
                                public void onClick(final AjaxRequestTarget target) {
                                    failureWindow.show(target);
                                }
                            };

                            switch (propTO.getStatus()) {

                                case SUCCESS:
                                case CREATED:
                                    image = new Image("icon",
                                            new ContextRelativeResource(IMG_PREFIX + Status.ACTIVE.toString()
                                                    + Constants.PNG_EXT));
                                    alt = "success icon";
                                    title = "success";
                                    failureWindow.setVisible(false);
                                    failureWindowLink.setEnabled(false);
                                    break;

                                default:
                                    image = new Image("icon",
                                            new ContextRelativeResource(IMG_PREFIX + Status.SUSPENDED.toString()
                                                    + Constants.PNG_EXT));
                                    alt = "failure icon";
                                    title = "failure";
                            }

                            image.add(new Behavior() {

                                private static final long serialVersionUID = 1469628524240283489L;

                                @Override
                                public void onComponentTag(final Component component, final ComponentTag tag) {
                                    tag.put("alt", alt);
                                    tag.put("title", title);
                                }
                            });
                            final FailureMessageModal executionFailureMessagePage;
                            if (propTO.getFailureReason() == null) {
                                executionFailureMessagePage = new FailureMessageModal(
                                        modal, pageRef, StringUtils.EMPTY);
                            } else {
                                executionFailureMessagePage = new FailureMessageModal(
                                        modal, pageRef, propTO.getFailureReason());
                            }

                            failureWindow.setContent(executionFailureMessagePage);
                        }
                    };
            fragment.add(propRes);
        }

        final AjaxLink<Void> close = new IndicatingAjaxLink<Void>("close") {

            private static final long serialVersionUID = -7978723352517770644L;

            @Override
            public void onClick(final AjaxRequestTarget target) {
                builder.modal.close(target);
            }
        };
        container.add(close);

        setOutputMarkupId(true);
    }

    /**
     * Get remote attributes list view.
     *
     * @param propTO propagation TO.
     * @return list view.
     */
    private ListView<String> getConnObjectView(final PropagationStatus propTO) {
        final ConnObjectTO before = propTO.getBeforeObj();
        final ConnObjectTO after = propTO.getAfterObj();

        // sorted in reversed presentation order
        final List<String> head = new ArrayList<String>();
        if (provResult.getAny() instanceof UserTO) {
            head.add(ConnIdSpecialAttributeName.PASSWORD);
            head.add(ConnIdSpecialAttributeName.ENABLE);
        }
        head.add(ConnIdSpecialAttributeName.UID);
        head.add(ConnIdSpecialAttributeName.NAME);

        final Map<String, AttrTO> beforeAttrMap = before == null
                ? Collections.<String, AttrTO>emptyMap()
                : before.getPlainAttrMap();

        final Map<String, AttrTO> afterAttrMap = after == null
                ? Collections.<String, AttrTO>emptyMap()
                : after.getPlainAttrMap();

        final Set<String> attributes = new HashSet<String>();
        attributes.addAll(beforeAttrMap.keySet());
        attributes.addAll(afterAttrMap.keySet());

        if (!(provResult.getAny() instanceof UserTO)) {
            attributes.remove(ConnIdSpecialAttributeName.PASSWORD);
            attributes.remove(ConnIdSpecialAttributeName.ENABLE);
        }

        final List<String> profile = new ArrayList<String>();
        profile.addAll(attributes);
        profile.removeAll(head);
        Collections.sort(profile);

        for (String attr : head) {
            if (attributes.contains(attr)) {
                profile.add(0, attr);
            }
        }

        return new ListView<String>("attrs", profile) {

            private static final long serialVersionUID = 4949588177564901031L;

            @Override
            protected void populateItem(final ListItem<String> item) {
                String name = item.getModelObject();

                final Fragment beforeValue;
                final Fragment afterValue;
                if (ConnIdSpecialAttributeName.ENABLE.equals(name)) {
                    beforeValue = getStatusIcon("beforeValue", propTO.getResource(), before);
                    afterValue = getStatusIcon("afterValue", propTO.getResource(), after);
                } else {
                    beforeValue = getLabelValue("beforeValue", name, beforeAttrMap);
                    afterValue = getLabelValue("afterValue", name, afterAttrMap);
                }

                item.add(new Label("attrName", new ResourceModel(name, name)));

                item.add(beforeValue);
                item.add(afterValue);
            }
        };
    }

    /**
     * Get fragment for attribute value (not remote status).
     *
     * @param id component id to be replaced with the fragment content.
     * @param attrName remote attribute name
     * @param attrMap remote attributes map.
     * @return fragment.
     */
    private Fragment getLabelValue(final String id, final String attrName, final Map<String, AttrTO> attrMap) {
        final String value;

        final AttrTO attr = attrMap.get(attrName);

        if (attr == null || attr.getValues() == null || attr.getValues().isEmpty()) {
            value = "";
        } else {
            if (ConnIdSpecialAttributeName.PASSWORD.equals(attrName)) {
                value = "********";
            } else {
                value = attr.getValues().size() > 1
                        ? attr.getValues().toString()
                        : attr.getValues().get(0);
            }
        }

        Component label = new Label("value", value.length() > 50 ? value.substring(0, 50) + "..." : value).
                add(new Behavior() {

                    private static final long serialVersionUID = 1469628524240283489L;

                    @Override
                    public void onComponentTag(final Component component, final ComponentTag tag) {
                        tag.put("title", value);
                    }
                });

        final Fragment frag = new Fragment(id, "attrValueFrag", this);
        frag.add(label);

        return frag;
    }

    /**
     * Get fragment for user status icon.
     *
     * @param id component id to be replaced with the fragment content
     * @param resourceName resource name
     * @param objectTO connector object TO
     * @return fragment.
     */
    private Fragment getStatusIcon(final String id, final String resourceName, final ConnObjectTO objectTO) {
        final Image image;
        final String alt, title;
        switch (statusUtils.getStatusBean(
                provResult.getAny(), resourceName, objectTO, this.provResult.getAny() instanceof GroupTO).getStatus()) {

            case ACTIVE:
                image = new Image("status",
                        new ContextRelativeResource(IMG_PREFIX + Status.ACTIVE.toString() + Constants.PNG_EXT));
                alt = "active icon";
                title = "Enabled";
                break;

            case SUSPENDED:
                image = new Image("status",
                        new ContextRelativeResource(IMG_PREFIX + Status.SUSPENDED.toString() + Constants.PNG_EXT));
                alt = "inactive icon";
                title = "Disabled";
                break;

            default:
                image = null;
                alt = null;
                title = null;
        }

        final Fragment frag;
        if (image == null) {
            frag = new Fragment(id, "emptyFrag", this);
        } else {
            image.add(new Behavior() {

                private static final long serialVersionUID = 1469628524240283489L;

                @Override
                public void onComponentTag(final Component component, final ComponentTag tag) {
                    tag.put("alt", alt);
                    tag.put("title", title);
                    tag.put("width", "12px");
                    tag.put("height", "12px");
                }
            });

            frag = new Fragment(id, "remoteStatusFrag", this);
            frag.add(image);
        }

        return frag;
    }
}
