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
package org.apache.syncope.core.persistence.api.dao.search;

import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Search condition to be applied when comparing bean field values.
 */
public class AnyCond extends AttributeCond {

    private static final long serialVersionUID = -1880319220462653955L;

    public AnyCond() {
        super();
    }

    public AnyCond(final Type conditionType) {
        super(conditionType);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).appendSuper(super.toString()).build();
    }
}
