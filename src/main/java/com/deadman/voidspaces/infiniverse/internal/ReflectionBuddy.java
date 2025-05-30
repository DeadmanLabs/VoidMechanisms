package com.deadman.voidspaces.infiniverse.internal;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;

import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistryAccess.ImmutableRegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import net.neoforged.fml.util.ObfuscationReflectionHelper;

public class ReflectionBuddy {
    public static <FIELDHOLDER,FIELDTYPE> Function<FIELDHOLDER,FIELDTYPE> getInstanceFieldGetter(Class<FIELDHOLDER> fieldHolderClass, String fieldName)
    {
        Field field = ObfuscationReflectionHelper.findField(fieldHolderClass, fieldName);
        return getInstanceFieldGetter(field);
    }
    public static <FIELDHOLDER,FIELDTYPE> MutableInstanceField<FIELDHOLDER,FIELDTYPE> getInstanceField(Class<FIELDHOLDER> fieldHolderClass, String fieldName)
    {
        return new MutableInstanceField<>(fieldHolderClass, fieldName);
    }
    private static <FIELDHOLDER,FIELDTYPE> Function<FIELDHOLDER,FIELDTYPE> getInstanceFieldGetter(Field field)
    {
        return instance -> {
            try
            {
                return (FIELDTYPE)(field.get(instance));
            }
            catch (IllegalArgumentException | IllegalAccessException e)
            {
                throw new RuntimeException(e);
            }
        };
    }
    public static class MutableInstanceField<FIELDHOLDER, FIELDTYPE>
    {
        private final Function<FIELDHOLDER,FIELDTYPE> getter;
        private final BiConsumer<FIELDHOLDER,FIELDTYPE> setter;

        private MutableInstanceField(Class<FIELDHOLDER> fieldHolderClass, String fieldName)
        {
            Field field = ObfuscationReflectionHelper.findField(fieldHolderClass, fieldName);
            this.getter = getInstanceFieldGetter(field);
            this.setter = getInstanceFieldSetter(field);
        }
        public FIELDTYPE get(FIELDHOLDER instance)
        {
            return this.getter.apply(instance);
        }
        public void set(FIELDHOLDER instance, FIELDTYPE value)
        {
            this.setter.accept(instance, value);
        }

        private static <FIELDHOLDER, FIELDTYPE> BiConsumer<FIELDHOLDER, FIELDTYPE> getInstanceFieldSetter(Field field)
        {
            return (instance,value) -> {
                try
                {
                    field.set(instance, value);
                }
                catch (IllegalArgumentException | IllegalAccessException e)
                {
                    throw new RuntimeException(e);
                }
            };
        }
    }

    public static class MinecraftServerAccess
    {
        // we need to read some private fields in MinecraftServer
        // we can use Access Transformers, Accessor Mixins, or ObfuscationReflectionHelper to get at these
        // we'll use ORH here as ATs and Mixins seem to be causing headaches for dependant mods lately
        // it also lets us define the private-field-getting-shenanigans in the same class we're using them
        // it also doesn't need any extra resources or buildscript stuff, which makes this example simpler to describe
        public static final Function<MinecraftServer, ChunkProgressListenerFactory> progressListenerFactory =
                getInstanceFieldGetter(MinecraftServer.class, "progressListenerFactory");
        public static final Function<MinecraftServer, Executor> executor =
                getInstanceFieldGetter(MinecraftServer.class, "executor");
        public static final Function<MinecraftServer, LevelStorageAccess> storageSource =
                getInstanceFieldGetter(MinecraftServer.class, "storageSource");
        public static final Function<MinecraftServer, LayeredRegistryAccess<RegistryLayer>> registries =
                getInstanceFieldGetter(MinecraftServer.class, "registries");
    }
    public static class WorldBorderAccess
    {
        public static final Function<WorldBorder, List<BorderChangeListener>> listeners =
                getInstanceFieldGetter(WorldBorder.class, "listeners");
    }

    public static class DelegateBorderChangeListenerAccess
    {
        public static final Function<BorderChangeListener.DelegateBorderChangeListener, WorldBorder> worldBorder =
                getInstanceFieldGetter(BorderChangeListener.DelegateBorderChangeListener.class, "worldBorder");
    }

    public static class LayeredRegistryAccessAccess
    {
        @SuppressWarnings("rawtypes")
        public static final MutableInstanceField<LayeredRegistryAccess, List<RegistryAccess.Frozen>> values =
                getInstanceField(LayeredRegistryAccess.class, "values");
        @SuppressWarnings("rawtypes")
        public static final Function<LayeredRegistryAccess, RegistryAccess.Frozen> composite =
                getInstanceFieldGetter(LayeredRegistryAccess.class, "composite");
    }

    public static class ImmutableRegistryAccessAccess
    {
        public static final MutableInstanceField<ImmutableRegistryAccess, Map<? extends ResourceKey<? extends Registry<?>>, ? extends Registry<?>>> registries =
                getInstanceField(ImmutableRegistryAccess.class, "registries");
    }
}