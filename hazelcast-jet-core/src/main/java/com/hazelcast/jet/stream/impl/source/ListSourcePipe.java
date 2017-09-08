/*
 * Copyright (c) 2008-2017, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.jet.stream.impl.source;

import com.hazelcast.core.IList;
import com.hazelcast.jet.ProcessorMetaSupplier;
import com.hazelcast.jet.processor.SourceProcessors;
import com.hazelcast.jet.stream.impl.pipeline.AbstractSourcePipe;
import com.hazelcast.jet.stream.impl.pipeline.StreamContext;

public class ListSourcePipe<E> extends AbstractSourcePipe<E> {

    private final IList<E> list;

    public ListSourcePipe(StreamContext context, IList<E> list) {
        super(context);
        this.list = list;
    }

    @Override
    protected ProcessorMetaSupplier getSourceMetaSupplier() {
        return SourceProcessors.readList(list.getName());
    }

    @Override
    protected String getName() {
        return "read-list-" + list.getName();
    }

    @Override
    public boolean isOrdered() {
        return true;
    }
}
