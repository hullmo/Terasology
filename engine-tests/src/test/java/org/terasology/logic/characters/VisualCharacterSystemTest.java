/*
 * Copyright 2017 MovingBlocks
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
package org.terasology.logic.characters;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.terasology.context.Context;
import org.terasology.context.internal.ContextImpl;
import org.terasology.engine.modes.loadProcesses.AwaitedLocalCharacterSpawnEvent;
import org.terasology.entitySystem.entity.EntityBuilder;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.entity.lifecycleEvents.OnActivatedComponent;
import org.terasology.entitySystem.event.Event;
import org.terasology.logic.characters.events.CreateVisualCharacterEvent;
import org.terasology.logic.players.LocalPlayer;
import org.terasology.registry.InjectionHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link VisualCharacterSystem}
 */
public class VisualCharacterSystemTest {
    private LocalPlayer localPlayer;
    private VisualCharacterSystem system;
    private EntityManager entityManager;

    private EntityRef clientEntityReturnedByLocalPlayer = EntityRef.NULL;
    /**
     * Next entity id used for mocked {@link EntityRef}s, 0 does not get used as {@link EntityRef#NULL} uses it.
     */
    private long nextEntityId = 1;

    @Before
    public void setup() throws Exception {
        this.system = new VisualCharacterSystem();
        Context context = new ContextImpl();

        this.localPlayer = Mockito.mock(LocalPlayer.class);
        context.put(LocalPlayer.class, localPlayer);
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return clientEntityReturnedByLocalPlayer;
            }
        }).when(localPlayer).getClientEntity();

        this.entityManager = Mockito.mock(EntityManager.class);

        Mockito.doReturn(Mockito.mock(EntityBuilder.class)).when(entityManager).newBuilder();

        context.put(EntityManager.class, this.entityManager);
        InjectionHelper.inject(system, context);
        system.setCreateAndAttachVisualEntityStrategy((entityBuilder, characterEntity) -> Mockito.mock(EntityRef.class));

    }

    private void recordEntityEventsToList(EntityRef entityRefMock, List<Event> listToFill) {
        Mockito.when(entityRefMock.send(Mockito.any())).then(new Answer<Event>() {
            @Override
            public Event answer(InvocationOnMock invocation) throws Throwable {
                Event event = invocation.getArgument(0);
                listToFill.add(event);
                return event;
            }
        });
    }

    /**
     * This test verifies that:
     * <ul>
     *     <li>No visual character gets created (via event) for the own character (as it is first person)</li>
     *     <li>That the system can deal with LocalPlayer and characters not being properly linked when
     *     the character entities get loaded/created</li>
     *     <li>A visual character gets created (via event) for characters that were already present when the player
     *     joined</li>
     *     <li>A visual character gets created (via event) for characters that joins afterwards </li>
     * </ul>
     */
    @Test
    public void testSendingOfCreateVisualCharacterEvent() {
        EntityRef clientEntity = mockEntityWithUniqueId();
        EntityRef otherClientEntity = mockEntityWithUniqueId();

        EntityRef ownCharacterEntity = mockEntityWithUniqueId();
        List<Event> ownCharacterEntityEvents = new ArrayList<>();
        recordEntityEventsToList(ownCharacterEntity, ownCharacterEntityEvents);
        VisualCharacterComponent visualComponentOfOwnCharacter = new VisualCharacterComponent();
        Mockito.when(ownCharacterEntity.getComponent(VisualCharacterComponent.class)).thenReturn(visualComponentOfOwnCharacter);

        EntityRef otherCharacterEntity = mockEntityWithUniqueId();
        List<Event> otherCharacterEntityEvents = new ArrayList<>();
        recordEntityEventsToList(otherCharacterEntity, otherCharacterEntityEvents);

        clientEntityReturnedByLocalPlayer = EntityRef.NULL;


        /*
         * Simulate activation before entity is done
         * since the character is not properly linked yet nothing should happen
         */
        system.onActivatedVisualCharacter(OnActivatedComponent.newInstance(), otherCharacterEntity,
                new VisualCharacterComponent());
        system.onActivatedVisualCharacter(OnActivatedComponent.newInstance(), ownCharacterEntity,
                visualComponentOfOwnCharacter);

        simulateProperLinkingOfLocalPlayerAndCharacterEntities(clientEntity, otherClientEntity,
                ownCharacterEntity, otherCharacterEntity);


        system.onAwaitedLocalCharacterSpawnEvent(new AwaitedLocalCharacterSpawnEvent(), ownCharacterEntity);


        assertTypesInListEqual(ownCharacterEntityEvents, Collections.emptyList());
        assertTypesInListEqual(otherCharacterEntityEvents, Arrays.asList(CreateVisualCharacterEvent.class));


        EntityRef laterJoiningCharacterEntity = mockEntityWithUniqueId();
        List<Event> laterJoiningCharacterEntityEvents = new ArrayList<>();
        recordEntityEventsToList(laterJoiningCharacterEntity, laterJoiningCharacterEntityEvents);

        // Joined player is not properly linked but it should not matter:
        Mockito.when(laterJoiningCharacterEntity.getOwner()).thenReturn(EntityRef.NULL);

        system.onActivatedVisualCharacter(OnActivatedComponent.newInstance(), laterJoiningCharacterEntity,
                new VisualCharacterComponent());


        /*
         * There is no second AwaitedLocalCharacterSpawnEvent event,
         * the system must use the activation to send the event:
         */
        assertTypesInListEqual(laterJoiningCharacterEntityEvents, Arrays.asList(CreateVisualCharacterEvent.class));

    }

    private EntityRef mockEntityWithUniqueId() {
        EntityRef entityRef = Mockito.mock(EntityRef.class);
        // Proper getId and exists method are needed to make equals work properly.
        Mockito.when(entityRef.getId()).thenReturn(nextEntityId++);
        Mockito.when(entityRef.exists()).thenReturn(true);
        return entityRef;
    }

    private void simulateProperLinkingOfLocalPlayerAndCharacterEntities(EntityRef clientEntity, EntityRef otherClientEntity, EntityRef ownCharacterEntity, EntityRef otherCharacterEntity) {
        // Simulate linking of character with client
        clientEntityReturnedByLocalPlayer = clientEntity;
        Mockito.when(ownCharacterEntity.getOwner()).thenReturn(clientEntity);
        Mockito.when(otherCharacterEntity.getOwner()).thenReturn(otherClientEntity);
        Mockito.when(entityManager.getEntitiesWith(VisualCharacterComponent.class)).thenReturn(Arrays.asList(otherCharacterEntity, ownCharacterEntity));

    }

    private void assertTypesInListEqual(List<Event> events, List<Class<?>> expectedTypes) {
        List<Class<?>> actualTypesList = typesOf(events);
        assertEquals(expectedTypes, actualTypesList);

    }

    private List<Class<?>> typesOf(List<?> objects) {
        List<Class<?>> typeList = new ArrayList<>();
        for (Object object: objects) {
            typeList.add(object.getClass());
        }
        return typeList;
    }
}
