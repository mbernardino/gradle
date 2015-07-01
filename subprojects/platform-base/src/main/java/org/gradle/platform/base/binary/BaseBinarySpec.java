/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.platform.base.binary;

import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Incubating;
import org.gradle.api.internal.AbstractBuildableModelElement;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.DefaultPolymorphicNamedEntityInstantiator;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.internal.rules.NamedDomainObjectFactoryRegistry;
import org.gradle.internal.Actions;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.ObjectInstantiationException;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.DomainObjectCollectionBackedModelMap;
import org.gradle.model.internal.core.ModelMapGroovyDecorator;
import org.gradle.platform.base.BinaryTasksCollection;
import org.gradle.platform.base.ModelInstantiationException;
import org.gradle.platform.base.internal.BinaryBuildAbility;
import org.gradle.platform.base.internal.BinarySpecInternal;
import org.gradle.platform.base.internal.DefaultBinaryTasksCollection;
import org.gradle.platform.base.internal.FixedBuildAbility;

/**
 * Base class for custom binary implementations.
 * A custom implementation of {@link org.gradle.platform.base.BinarySpec} must extend this type.
 *
 * TODO at the moment leaking BinarySpecInternal here to generate lifecycleTask in
 * LanguageBasePlugin$createLifecycleTaskForBinary#createLifecycleTaskForBinary rule
 *
 */
@Incubating
public abstract class BaseBinarySpec extends AbstractBuildableModelElement implements BinarySpecInternal {
    private final NamedDomainObjectFactoryRegistry<LanguageSourceSet> entityInstantiator;
    private final ModelMap<LanguageSourceSet> ownedSourceSets;
    private final DomainObjectSet<LanguageSourceSet> inputSourceSets = new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet.class);

    private static ThreadLocal<BinaryInfo> nextBinaryInfo = new ThreadLocal<BinaryInfo>();
    private final BinaryTasksCollection tasks;

    private final String name;
    private final String typeName;

    private boolean disabled;

    public static <T extends BaseBinarySpec> T create(Class<T> type, String name, Instantiator instantiator, ITaskFactory taskFactory) {
        if (type.equals(BaseBinarySpec.class)) {
            throw new ModelInstantiationException("Cannot create instance of abstract class BaseBinarySpec.");
        }
        nextBinaryInfo.set(new BinaryInfo(name, type.getSimpleName(), taskFactory, instantiator));
        try {
            try {
                return instantiator.newInstance(type);
            } catch (ObjectInstantiationException e) {
                throw new ModelInstantiationException(String.format("Could not create binary of type %s", type.getSimpleName()), e.getCause());
            }
        } finally {
            nextBinaryInfo.set(null);
        }
    }

    protected BaseBinarySpec() {
        this(nextBinaryInfo.get());
    }

    private BaseBinarySpec(BinaryInfo info) {
        if (info == null) {
            throw new ModelInstantiationException("Direct instantiation of a BaseBinarySpec is not permitted. Use a BinaryTypeBuilder instead.");
        }
        this.name = info.name;
        this.typeName = info.typeName;
        this.tasks = info.instantiator.newInstance(DefaultBinaryTasksCollection.class, this, info.taskFactory);
        DefaultPolymorphicNamedEntityInstantiator<LanguageSourceSet> entityInstantiator = new DefaultPolymorphicNamedEntityInstantiator<LanguageSourceSet>(LanguageSourceSet.class, "owned sources");
        this.entityInstantiator = entityInstantiator;
        this.ownedSourceSets = new DomainObjectCollectionBackedModelMap<LanguageSourceSet>(
            LanguageSourceSet.class,
            new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet.class),
            entityInstantiator,
            new Namer(),
            Actions.doNothing());
    }

    protected String getTypeName() {
        return typeName;
    }

    public String getDisplayName() {
        return String.format("%s '%s'", getTypeName(), getName());
    }

    public String getName() {
        return name;
    }

    @Override
    public void setBuildable(boolean buildable) {
        this.disabled = !buildable;
    }

    public final boolean isBuildable() {
        return getBuildAbility().isBuildable();
    }

    @Override
    public DomainObjectSet<LanguageSourceSet> getSource() {
        return new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet.class, ownedSourceSets.values());
    }

    public void sources(Action<? super ModelMap<LanguageSourceSet>> action) {
        action.execute(getSources());
    }

    @Override
    public NamedDomainObjectFactoryRegistry<LanguageSourceSet> getEntityInstantiator() {
        return entityInstantiator;
    }

    @Override
    public DomainObjectSet<LanguageSourceSet> getInputs() {
        return inputSourceSets;
    }

    @Override
    public ModelMap<LanguageSourceSet> getSources() {
        return ModelMapGroovyDecorator.wrap(ownedSourceSets);
    }

    public BinaryTasksCollection getTasks() {
        return tasks;
    }

    @Override
    public void tasks(Action<? super BinaryTasksCollection> action) {
        action.execute(tasks);
    }

    public boolean isLegacyBinary() {
        return false;
    }

    private static class BinaryInfo {
        private final String name;
        private final String typeName;
        private final ITaskFactory taskFactory;
        private final Instantiator instantiator;

        private BinaryInfo(String name, String typeName, ITaskFactory taskFactory, Instantiator instantiator) {
            this.name = name;
            this.typeName = typeName;
            this.taskFactory = taskFactory;
            this.instantiator = instantiator;
        }
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public final BinaryBuildAbility getBuildAbility() {
        if (disabled) {
            return new FixedBuildAbility(false);
        }
        return getBinaryBuildAbility();
    }

    protected BinaryBuildAbility getBinaryBuildAbility() {
        // Default behavior is to always be buildable.  Binary implementations should define what
        // criteria make them buildable or not.
        return new FixedBuildAbility(true);
    }
}
