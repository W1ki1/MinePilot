package pl.pixeloza.mc_ai_recorder.client.recording;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class GuiInteractionRecorder {
    private static final AtomicLong NEXT_INTERACTION_ID =
            new AtomicLong(1);

    private static final Map<Screen, PendingClick> PENDING_CLICKS =
            new WeakHashMap<>();

    private static final Map<Screen, PendingDragStep> PENDING_DRAG_STEPS =
            new WeakHashMap<>();

    private static final Map<Screen, PendingRelease> PENDING_RELEASES =
            new WeakHashMap<>();

    /*
     * Drag nie staje się aktywny od minimalnego ruchu myszy.
     * Aktywujemy go dopiero, gdy kursor faktycznie przejdzie
     * do innego slotu albo poza slot.
     *
     * Usuwa to fałszywe:
     * DRAG_START -> DRAG_END
     * powstające przy zwykłym kliknięciu i ruchu o 0.2 px.
     */
    private static final Map<Screen, DragState> DRAG_STATES =
            new WeakHashMap<>();

    /*
     * Pozwala odrzucić release prawego przycisku, którym
     * gracz właśnie otworzył crafting table lub inny kontener.
     */
    private static final Map<Screen, Long> SCREEN_OPEN_TICKS =
            new WeakHashMap<>();

    private GuiInteractionRecorder() {
    }

    public static void register(
            RecordingManager recordingManager
    ) {
        ScreenEvents.AFTER_INIT.register(
                (client, screen, width, height) -> {
                    if (!(screen instanceof AbstractContainerScreen<?>)) {
                        return;
                    }

                    SCREEN_OPEN_TICKS.put(
                            screen,
                            recordingManager.getCurrentTick()
                    );

                    recordScreenEvent(
                            recordingManager,
                            screen,
                            "SCREEN_OPEN",
                            "OPEN"
                    );

                    ScreenMouseEvents.beforeMouseClick(screen).register(
                            (currentScreen, event) ->
                                    captureBeforeClick(
                                            currentScreen,
                                            event
                                    )
                    );

                    ScreenMouseEvents.afterMouseClick(screen).register(
                            (currentScreen, event, consumed) -> {
                                recordAfterClick(
                                        recordingManager,
                                        currentScreen,
                                        event
                                );

                                return false;
                            }
                    );

                    ScreenMouseEvents.beforeMouseDrag(screen).register(
                            (
                                    currentScreen,
                                    event,
                                    deltaX,
                                    deltaY
                            ) -> captureBeforeDrag(
                                    currentScreen,
                                    event,
                                    deltaX,
                                    deltaY
                            )
                    );

                    ScreenMouseEvents.afterMouseDrag(screen).register(
                            (
                                    currentScreen,
                                    event,
                                    deltaX,
                                    deltaY,
                                    consumed
                            ) -> {
                                recordAfterDrag(
                                        recordingManager,
                                        currentScreen,
                                        event,
                                        deltaX,
                                        deltaY
                                );

                                return false;
                            }
                    );

                    ScreenMouseEvents.beforeMouseRelease(screen).register(
                            (currentScreen, event) ->
                                    captureBeforeRelease(
                                            currentScreen,
                                            event
                                    )
                    );

                    ScreenMouseEvents.afterMouseRelease(screen).register(
                            (currentScreen, event, consumed) -> {
                                recordAfterRelease(
                                        recordingManager,
                                        currentScreen,
                                        event
                                );

                                return false;
                            }
                    );

                    ScreenMouseEvents.afterMouseScroll(screen).register(
                            (
                                    currentScreen,
                                    mouseX,
                                    mouseY,
                                    horizontalAmount,
                                    verticalAmount,
                                    consumed
                            ) -> {
                                recordMouseScroll(
                                        recordingManager,
                                        currentScreen,
                                        mouseX,
                                        mouseY,
                                        horizontalAmount,
                                        verticalAmount
                                );

                                return false;
                            }
                    );

                    ScreenKeyboardEvents.afterKeyPress(screen).register(
                            (currentScreen, event) ->
                                    recordKeyPress(
                                            recordingManager,
                                            currentScreen,
                                            event
                                    )
                    );

                    ScreenEvents.remove(screen).register(
                            removedScreen -> {
                                clearScreenState(
                                        removedScreen
                                );

                                recordScreenEvent(
                                        recordingManager,
                                        removedScreen,
                                        "SCREEN_CLOSE",
                                        "CLOSE"
                                );
                            }
                    );
                }
        );

        System.out.println(
                "[MC AI Recorder] GUI interaction recorder registered"
        );
    }

    public static void recordCurrentScreenOpen(
            RecordingManager recordingManager,
            Screen screen
    ) {
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            return;
        }

        SCREEN_OPEN_TICKS.put(
                screen,
                recordingManager.getCurrentTick()
        );

        recordScreenEvent(
                recordingManager,
                screen,
                "SCREEN_OPEN",
                "OPEN"
        );
    }

    private static void captureBeforeClick(
            Screen screen,
            Object event
    ) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        /*
         * Nowe kliknięcie kończy ewentualny stary,
         * niedokończony stan dragowania.
         */
        DRAG_STATES.remove(screen);
        PENDING_DRAG_STEPS.remove(screen);
        PENDING_RELEASES.remove(screen);

        long interactionId =
                NEXT_INTERACTION_ID.getAndIncrement();

        PENDING_CLICKS.put(
                screen,
                new PendingClick(
                        interactionId,
                        readMouseButton(event),
                        readMouseX(event),
                        readMouseY(event),

                        readHoveredSlot(
                                containerScreen
                        ),

                        readStack(
                                containerScreen
                                        .getMenu()
                                        .getCarried()
                        ),

                        isShiftDown(),
                        isControlDown(),
                        isAltDown()
                )
        );
    }

    private static void recordAfterClick(
            RecordingManager recordingManager,
            Screen screen,
            Object event
    ) {
        if (!recordingManager.isRecording()) {
            return;
        }

        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        PendingClick pending =
                PENDING_CLICKS.get(screen);

        double mouseX =
                readMouseX(event);

        double mouseY =
                readMouseY(event);

        int button =
                readMouseButton(event);

        if (pending != null) {
            if (mouseX < 0.0) {
                mouseX = pending.mouseX();
            }

            if (mouseY < 0.0) {
                mouseY = pending.mouseY();
            }

            if (button < 0) {
                button = pending.button();
            }
        }

        SlotInfo slotAfter =
                readHoveredSlot(
                        containerScreen
                );

        StackInfo carriedAfter =
                readStack(
                        containerScreen
                                .getMenu()
                                .getCarried()
                );

        SlotInfo slotBefore =
                pending != null
                        ? pending.slotBefore()
                        : slotAfter;

        StackInfo carriedBefore =
                pending != null
                        ? pending.carriedBefore()
                        : carriedAfter;

        boolean shift =
                pending != null
                        ? pending.shiftDown()
                        : isShiftDown();

        boolean control =
                pending != null
                        ? pending.controlDown()
                        : isControlDown();

        boolean alt =
                pending != null
                        ? pending.altDown()
                        : isAltDown();

        long interactionId =
                pending != null
                        ? pending.interactionId()
                        : NEXT_INTERACTION_ID
                        .getAndIncrement();

        SlotInfo selectedSlot =
                slotAfter.slotId() >= 0
                        ? slotAfter
                        : slotBefore;

        recordingManager.recordGuiInteraction(
                createSnapshot(
                        recordingManager,
                        screen,

                        "MOUSE_CLICK",
                        inferMouseAction(
                                button,
                                shift,
                                selectedSlot.slotId()
                        ),

                        button,
                        -1,
                        0,

                        mouseX,
                        mouseY,

                        0.0,
                        0.0,

                        selectedSlot,

                        shift,
                        control,
                        alt,

                        slotBefore.stack(),
                        slotAfter.stack(),

                        carriedBefore,
                        carriedAfter,

                        interactionId,
                        0.0,
                        0.0
                )
        );
    }

    private static void captureBeforeDrag(
            Screen screen,
            Object event,
            double deltaX,
            double deltaY
    ) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        PENDING_DRAG_STEPS.put(
                screen,
                new PendingDragStep(
                        readMouseButton(event),
                        readMouseX(event),
                        readMouseY(event),

                        deltaX,
                        deltaY,

                        readHoveredSlot(
                                containerScreen
                        ),

                        readStack(
                                containerScreen
                                        .getMenu()
                                        .getCarried()
                        ),

                        isShiftDown(),
                        isControlDown(),
                        isAltDown()
                )
        );
    }

    private static void recordAfterDrag(
            RecordingManager recordingManager,
            Screen screen,
            Object event,
            double deltaX,
            double deltaY
    ) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        if (!recordingManager.isRecording()) {
            PENDING_DRAG_STEPS.remove(screen);
            return;
        }

        PendingDragStep pendingStep =
                PENDING_DRAG_STEPS.remove(screen);

        PendingClick pendingClick =
                PENDING_CLICKS.get(screen);

        double mouseX =
                readMouseX(event);

        double mouseY =
                readMouseY(event);

        int button =
                readMouseButton(event);

        if (pendingStep != null) {
            if (mouseX < 0.0) {
                mouseX = pendingStep.mouseX();
            }

            if (mouseY < 0.0) {
                mouseY = pendingStep.mouseY();
            }

            if (button < 0) {
                button = pendingStep.button();
            }

            deltaX = pendingStep.deltaX();
            deltaY = pendingStep.deltaY();
        }

        if (button < 0 && pendingClick != null) {
            button = pendingClick.button();
        }

        SlotInfo slotAfter =
                readHoveredSlot(
                        containerScreen
                );

        StackInfo carriedAfter =
                readStack(
                        containerScreen
                                .getMenu()
                                .getCarried()
                );

        SlotInfo slotBefore =
                pendingStep != null
                        ? pendingStep.slotBefore()
                        : slotAfter;

        StackInfo carriedBefore =
                pendingStep != null
                        ? pendingStep.carriedBefore()
                        : carriedAfter;

        boolean shift =
                pendingStep != null
                        ? pendingStep.shiftDown()
                        : isShiftDown();

        boolean control =
                pendingStep != null
                        ? pendingStep.controlDown()
                        : isControlDown();

        boolean alt =
                pendingStep != null
                        ? pendingStep.altDown()
                        : isAltDown();

        DragState dragState =
                DRAG_STATES.get(screen);

        if (dragState == null) {
            long interactionId =
                    pendingClick != null
                            ? pendingClick.interactionId()
                            : NEXT_INTERACTION_ID
                            .getAndIncrement();

            SlotInfo startSlot =
                    pendingClick != null
                            ? pendingClick.slotBefore()
                            : slotBefore;

            int startButton =
                    pendingClick != null
                            ? pendingClick.button()
                            : button;

            dragState =
                    new DragState(
                            interactionId,
                            startButton,
                            startSlot
                    );

            DRAG_STATES.put(
                    screen,
                    dragState
            );
        }

        int currentSlotId =
                slotAfter.slotId();

        /*
         * Najważniejsza poprawka:
         *
         * Sam event mouseDragged nie oznacza jeszcze
         * faktycznej operacji dragowania itemów. Minecraft
         * może go wywołać już po ruchu rzędu 0.2 px.
         *
         * Aktywujemy drag dopiero po wejściu do innego
         * slotu albo wyjściu poza slot.
         */
        if (!dragState.active()) {
            if (currentSlotId
                    == dragState.startSlot().slotId()) {
                return;
            }

            dragState.activate();

            recordingManager.recordGuiInteraction(
                    createSnapshot(
                            recordingManager,
                            screen,

                            "MOUSE_DRAG",
                            "DRAG_START",

                            dragState.button(),
                            -1,
                            0,

                            pendingClick != null
                                    ? pendingClick.mouseX()
                                    : mouseX,

                            pendingClick != null
                                    ? pendingClick.mouseY()
                                    : mouseY,

                            0.0,
                            0.0,

                            dragState.startSlot(),

                            shift,
                            control,
                            alt,

                            dragState
                                    .startSlot()
                                    .stack(),

                            dragState
                                    .startSlot()
                                    .stack(),

                            carriedBefore,
                            carriedBefore,

                            dragState.interactionId(),
                            deltaX,
                            deltaY
                    )
            );
        }

        /*
         * Zapisujemy tylko przejście do nowego slotu,
         * nie każdy ruch kursora o ułamek piksela.
         */
        if (currentSlotId
                != dragState.lastSlotId()) {

            String actionType =
                    currentSlotId >= 0
                            ? "DRAG_SLOT"
                            : "DRAG_OUTSIDE";

            recordingManager.recordGuiInteraction(
                    createSnapshot(
                            recordingManager,
                            screen,

                            "MOUSE_DRAG",
                            actionType,

                            dragState.button(),
                            -1,
                            0,

                            mouseX,
                            mouseY,

                            0.0,
                            0.0,

                            slotAfter,

                            shift,
                            control,
                            alt,

                            slotBefore.stack(),
                            slotAfter.stack(),

                            carriedBefore,
                            carriedAfter,

                            dragState.interactionId(),
                            deltaX,
                            deltaY
                    )
            );

            dragState.setLastSlotId(
                    currentSlotId
            );
        }
    }

    private static void captureBeforeRelease(
            Screen screen,
            Object event
    ) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        PENDING_RELEASES.put(
                screen,
                new PendingRelease(
                        readMouseButton(event),
                        readMouseX(event),
                        readMouseY(event),

                        readHoveredSlot(
                                containerScreen
                        ),

                        readStack(
                                containerScreen
                                        .getMenu()
                                        .getCarried()
                        ),

                        isShiftDown(),
                        isControlDown(),
                        isAltDown()
                )
        );
    }

    private static void recordAfterRelease(
            RecordingManager recordingManager,
            Screen screen,
            Object event
    ) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        PendingRelease pendingRelease =
                PENDING_RELEASES.remove(screen);

        PendingClick pendingClick =
                PENDING_CLICKS.remove(screen);

        PENDING_DRAG_STEPS.remove(screen);

        DragState dragState =
                DRAG_STATES.remove(screen);

        if (!recordingManager.isRecording()) {
            return;
        }

        Long openedAtTick =
                SCREEN_OPEN_TICKS.get(screen);

        /*
         * Odrzuca puszczenie prawego przycisku,
         * którym gracz właśnie otworzył crafting table.
         */
        if ((dragState == null || !dragState.active())
                && openedAtTick != null
                && openedAtTick.longValue()
                == recordingManager.getCurrentTick()) {

            System.out.println(
                    "[MC AI Recorder] Ignored release from screen opening"
            );

            return;
        }

        double mouseX =
                readMouseX(event);

        double mouseY =
                readMouseY(event);

        int button =
                readMouseButton(event);

        if (pendingRelease != null) {
            if (mouseX < 0.0) {
                mouseX = pendingRelease.mouseX();
            }

            if (mouseY < 0.0) {
                mouseY = pendingRelease.mouseY();
            }

            if (button < 0) {
                button = pendingRelease.button();
            }
        }

        if (button < 0 && pendingClick != null) {
            button = pendingClick.button();
        }

        SlotInfo slotAfter =
                readHoveredSlot(
                        containerScreen
                );

        StackInfo carriedAfter =
                readStack(
                        containerScreen
                                .getMenu()
                                .getCarried()
                );

        SlotInfo slotBefore =
                pendingRelease != null
                        ? pendingRelease.slotBefore()
                        : slotAfter;

        StackInfo carriedBefore =
                pendingRelease != null
                        ? pendingRelease.carriedBefore()
                        : carriedAfter;

        boolean shift =
                pendingRelease != null
                        ? pendingRelease.shiftDown()
                        : isShiftDown();

        boolean control =
                pendingRelease != null
                        ? pendingRelease.controlDown()
                        : isControlDown();

        boolean alt =
                pendingRelease != null
                        ? pendingRelease.altDown()
                        : isAltDown();

        boolean realDrag =
                dragState != null
                        && dragState.active();

        long interactionId;

        if (realDrag) {
            interactionId =
                    dragState.interactionId();
        } else if (pendingClick != null) {
            interactionId =
                    pendingClick.interactionId();
        } else {
            interactionId =
                    NEXT_INTERACTION_ID
                            .getAndIncrement();
        }

        recordingManager.recordGuiInteraction(
                createSnapshot(
                        recordingManager,
                        screen,

                        realDrag
                                ? "MOUSE_DRAG"
                                : "MOUSE_RELEASE",

                        realDrag
                                ? "DRAG_END"
                                : releaseActionName(button),

                        button,
                        -1,
                        0,

                        mouseX,
                        mouseY,

                        0.0,
                        0.0,

                        slotAfter,

                        shift,
                        control,
                        alt,

                        slotBefore.stack(),
                        slotAfter.stack(),

                        carriedBefore,
                        carriedAfter,

                        interactionId,
                        0.0,
                        0.0
                )
        );
    }

    private static void recordMouseScroll(
            RecordingManager recordingManager,
            Screen screen,
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount
    ) {
        if (!recordingManager.isRecording()) {
            return;
        }

        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        SlotInfo slot =
                readHoveredSlot(
                        containerScreen
                );

        StackInfo carried =
                readStack(
                        containerScreen
                                .getMenu()
                                .getCarried()
                );

        String actionType;

        if (verticalAmount > 0.0) {
            actionType = "SCROLL_UP";
        } else if (verticalAmount < 0.0) {
            actionType = "SCROLL_DOWN";
        } else {
            actionType = "SCROLL_HORIZONTAL";
        }

        recordingManager.recordGuiInteraction(
                createSnapshot(
                        recordingManager,
                        screen,

                        "MOUSE_SCROLL",
                        actionType,

                        -1,
                        -1,
                        0,

                        mouseX,
                        mouseY,

                        horizontalAmount,
                        verticalAmount,

                        slot,

                        isShiftDown(),
                        isControlDown(),
                        isAltDown(),

                        slot.stack(),
                        slot.stack(),

                        carried,
                        carried,

                        NEXT_INTERACTION_ID
                                .getAndIncrement(),

                        0.0,
                        0.0
                )
        );
    }

    private static void recordKeyPress(
            RecordingManager recordingManager,
            Screen screen,
            Object keyEvent
    ) {
        if (!recordingManager.isRecording()) {
            return;
        }

        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        int key =
                readInt(
                        keyEvent,
                        -1,
                        "key",
                        "keyCode",
                        "getKey"
                );

        int modifiers =
                readInt(
                        keyEvent,
                        0,
                        "modifiers",
                        "mods",
                        "getModifiers"
                );

        SlotInfo slot =
                readHoveredSlot(
                        containerScreen
                );

        StackInfo carried =
                readStack(
                        containerScreen
                                .getMenu()
                                .getCarried()
                );

        boolean shift =
                (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        boolean control =
                (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

        boolean alt =
                (modifiers & GLFW.GLFW_MOD_ALT) != 0;

        recordingManager.recordGuiInteraction(
                createSnapshot(
                        recordingManager,
                        screen,

                        "KEY_PRESS",
                        inferKeyAction(key),

                        -1,
                        key,
                        modifiers,

                        -1.0,
                        -1.0,

                        0.0,
                        0.0,

                        slot,

                        shift,
                        control,
                        alt,

                        slot.stack(),
                        slot.stack(),

                        carried,
                        carried,

                        NEXT_INTERACTION_ID
                                .getAndIncrement(),

                        0.0,
                        0.0
                )
        );
    }

    private static void recordScreenEvent(
            RecordingManager recordingManager,
            Screen screen,
            String eventType,
            String actionType
    ) {
        if (!recordingManager.isRecording()) {
            return;
        }

        recordingManager.prepareGuiScreenEvent(
                screen,
                eventType
        );

        SlotInfo emptySlot =
                SlotInfo.empty();

        StackInfo emptyStack =
                StackInfo.empty();

        recordingManager.recordGuiInteraction(
                createSnapshot(
                        recordingManager,
                        screen,

                        eventType,
                        actionType,

                        -1,
                        -1,
                        0,

                        -1.0,
                        -1.0,

                        0.0,
                        0.0,

                        emptySlot,

                        false,
                        false,
                        false,

                        emptyStack,
                        emptyStack,

                        emptyStack,
                        emptyStack,

                        0,
                        0.0,
                        0.0
                )
        );

        recordingManager.finishGuiScreenEvent(
                screen,
                eventType
        );
    }

    private static GuiInteractionSnapshot createSnapshot(
            RecordingManager recordingManager,
            Screen screen,

            String eventType,
            String actionType,

            int mouseButton,
            int keyCode,
            int modifiers,

            double mouseX,
            double mouseY,

            double scrollHorizontal,
            double scrollVertical,

            SlotInfo slot,

            boolean shiftDown,
            boolean controlDown,
            boolean altDown,

            StackInfo slotBefore,
            StackInfo slotAfter,

            StackInfo carriedBefore,
            StackInfo carriedAfter,

            long interactionId,
            double dragDeltaX,
            double dragDeltaY
    ) {
        Minecraft client =
                Minecraft.getInstance();

        int screenWidth =
                Math.max(
                        1,
                        client.getWindow()
                                .getGuiScaledWidth()
                );

        int screenHeight =
                Math.max(
                        1,
                        client.getWindow()
                                .getGuiScaledHeight()
                );

        double normalizedX =
                mouseX >= 0.0
                        ? mouseX / screenWidth
                        : -1.0;

        double normalizedY =
                mouseY >= 0.0
                        ? mouseY / screenHeight
                        : -1.0;

        String title =
                screen.getTitle() != null
                        ? screen.getTitle().getString()
                        : "";

        Integer semanticSlotId =
                slot.slotId() >= 0
                        ? slot.slotId()
                        : null;

        return new GuiInteractionSnapshot(
                "GUI_ACTION",
                recordingManager.nextSequenceId(),
                recordingManager.getCurrentTick(),
                System.currentTimeMillis(),
                recordingManager.getCurrentFramePath(),

                MenuSemantics.screenType(screen),
                MenuSemantics.menuType(screen),
                MenuSemantics.slotCount(screen),

                recordingManager.getCurrentContainerRevision(),
                recordingManager.getCurrentInventoryRevision(),

                new ScreenGeometrySnapshot(
                        screenWidth,
                        screenHeight,
                        client.getWindow()
                                .getGuiScale()
                ),

                normalizeEventType(
                        eventType,
                        actionType
                ),
                actionType,
                interactionId,

                mouseButton >= 0
                        ? mouseButton
                        : null,

                keyCode >= 0
                        ? keyCode
                        : null,

                new ModifiersSnapshot(
                        shiftDown,
                        controlDown,
                        altDown
                ),

                new PointerSnapshot(
                        mouseX,
                        mouseY,
                        normalizedX,
                        normalizedY
                ),

                new GuiSlotSnapshot(
                        semanticSlotId,
                        MenuSemantics.slotRole(
                                screen,
                                slot.slotId()
                        ),
                        null,
                        null
                ),

                slotBefore.snapshot(),
                slotAfter.snapshot(),
                carriedBefore.snapshot(),
                carriedAfter.snapshot(),

                title,
                modifiers,
                scrollHorizontal,
                scrollVertical,
                dragDeltaX,
                dragDeltaY,
                slot.slotX(),
                slot.slotY()
        );
    }

    private static String normalizeEventType(
            String eventType,
            String actionType
    ) {
        if ("MOUSE_SCROLL".equals(eventType)) {
            return "SCROLL";
        }

        if ("DRAG_START".equals(actionType)) {
            return "DRAG_START";
        }

        if ("DRAG_SLOT".equals(actionType)
                || "DRAG_OUTSIDE".equals(actionType)) {
            return "DRAG_SLOT";
        }

        if ("DRAG_END".equals(actionType)) {
            return "DRAG_END";
        }

        return eventType;
    }

    private static SlotInfo readHoveredSlot(
            AbstractContainerScreen<?> screen
    ) {
        Slot slot =
                findHoveredSlot(screen);

        if (slot == null) {
            return SlotInfo.empty();
        }

        int slotId =
                screen.getMenu()
                        .slots
                        .indexOf(slot);

        return new SlotInfo(
                slotId,
                slot.x,
                slot.y,
                readStack(
                        slot.getItem()
                )
        );
    }

    private static Slot findHoveredSlot(
            AbstractContainerScreen<?> screen
    ) {
        Class<?> currentClass =
                screen.getClass();

        /*
         * Najpierw szukamy pola typu Slot,
         * którego nazwa zawiera "hover".
         */
        while (currentClass != null) {
            for (Field field :
                    currentClass.getDeclaredFields()) {

                if (!Slot.class.isAssignableFrom(
                        field.getType()
                )) {
                    continue;
                }

                if (!field.getName()
                        .toLowerCase()
                        .contains("hover")) {
                    continue;
                }

                Slot value =
                        readSlotField(
                                screen,
                                field
                        );

                if (value != null) {
                    return value;
                }
            }

            currentClass =
                    currentClass.getSuperclass();
        }

        /*
         * Fallback na wypadek innej nazwy mappingu.
         */
        currentClass =
                screen.getClass();

        while (currentClass != null) {
            for (Field field :
                    currentClass.getDeclaredFields()) {

                if (!Slot.class.isAssignableFrom(
                        field.getType()
                )) {
                    continue;
                }

                Slot value =
                        readSlotField(
                                screen,
                                field
                        );

                if (value != null) {
                    return value;
                }
            }

            currentClass =
                    currentClass.getSuperclass();
        }

        return null;
    }

    private static Slot readSlotField(
            Object owner,
            Field field
    ) {
        try {
            field.setAccessible(true);

            Object value =
                    field.get(owner);

            if (value instanceof Slot slot) {
                return slot;
            }
        } catch (ReflectiveOperationException |
                 RuntimeException ignored) {
        }

        return null;
    }

    private static StackInfo readStack(
            ItemStack stack
    ) {
        return new StackInfo(
                ItemStackSnapshot.from(
                        stack
                )
        );
    }

    private static void clearScreenState(
            Screen screen
    ) {
        PENDING_CLICKS.remove(screen);
        PENDING_DRAG_STEPS.remove(screen);
        PENDING_RELEASES.remove(screen);
        DRAG_STATES.remove(screen);
        SCREEN_OPEN_TICKS.remove(screen);
    }

    private static double readMouseX(
            Object event
    ) {
        return readDouble(
                event,
                -1.0,
                "x",
                "mouseX",
                "getX"
        );
    }

    private static double readMouseY(
            Object event
    ) {
        return readDouble(
                event,
                -1.0,
                "y",
                "mouseY",
                "getY"
        );
    }

    private static int readMouseButton(
            Object event
    ) {
        return readInt(
                event,
                -1,
                "button",
                "getButton"
        );
    }

    private static boolean isShiftDown() {
        return isKeyDown(
                GLFW.GLFW_KEY_LEFT_SHIFT,
                GLFW.GLFW_KEY_RIGHT_SHIFT
        );
    }

    private static boolean isControlDown() {
        return isKeyDown(
                GLFW.GLFW_KEY_LEFT_CONTROL,
                GLFW.GLFW_KEY_RIGHT_CONTROL
        );
    }

    private static boolean isAltDown() {
        return isKeyDown(
                GLFW.GLFW_KEY_LEFT_ALT,
                GLFW.GLFW_KEY_RIGHT_ALT
        );
    }

    private static boolean isKeyDown(
            int leftKey,
            int rightKey
    ) {
        Minecraft client =
                Minecraft.getInstance();

        long window =
                client.getWindow()
                        .handle();

        return GLFW.glfwGetKey(
                window,
                leftKey
        ) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(
                window,
                rightKey
        ) == GLFW.GLFW_PRESS;
    }

    private static int readInt(
            Object object,
            int defaultValue,
            String... memberNames
    ) {
        Number number =
                readNumber(
                        object,
                        memberNames
                );

        return number != null
                ? number.intValue()
                : defaultValue;
    }

    private static double readDouble(
            Object object,
            double defaultValue,
            String... memberNames
    ) {
        Number number =
                readNumber(
                        object,
                        memberNames
                );

        return number != null
                ? number.doubleValue()
                : defaultValue;
    }

    private static Number readNumber(
            Object object,
            String... memberNames
    ) {
        if (object == null) {
            return null;
        }

        Class<?> objectClass =
                object.getClass();

        for (String memberName :
                memberNames) {
            try {
                Method method =
                        objectClass.getMethod(
                                memberName
                        );

                Object result =
                        method.invoke(object);

                if (result instanceof Number number) {
                    return number;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        Class<?> currentClass =
                objectClass;

        while (currentClass != null) {
            for (String memberName :
                    memberNames) {
                try {
                    Field field =
                            currentClass.getDeclaredField(
                                    memberName
                            );

                    field.setAccessible(true);

                    Object result =
                            field.get(object);

                    if (result instanceof Number number) {
                        return number;
                    }
                } catch (ReflectiveOperationException |
                         RuntimeException ignored) {
                }
            }

            currentClass =
                    currentClass.getSuperclass();
        }

        return null;
    }

    private static String inferMouseAction(
            int button,
            boolean shift,
            int slotId
    ) {
        String suffix =
                slotId >= 0
                        ? ""
                        : "_OUTSIDE";

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return shift
                    ? "SHIFT_LEFT_CLICK" + suffix
                    : "LEFT_CLICK" + suffix;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return shift
                    ? "SHIFT_RIGHT_CLICK" + suffix
                    : "RIGHT_CLICK" + suffix;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            return "MIDDLE_CLICK" + suffix;
        }

        return "MOUSE_BUTTON_"
                + button
                + suffix;
    }

    private static String releaseActionName(
            int button
    ) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return "LEFT_RELEASE";
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return "RIGHT_RELEASE";
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            return "MIDDLE_RELEASE";
        }

        return "MOUSE_RELEASE_"
                + button;
    }

    private static String inferKeyAction(
            int key
    ) {
        if (key >= GLFW.GLFW_KEY_1
                && key <= GLFW.GLFW_KEY_9) {

            int slotNumber =
                    key
                            - GLFW.GLFW_KEY_1
                            + 1;

            return "HOTBAR_SWAP_"
                    + slotNumber;
        }

        if (key == GLFW.GLFW_KEY_Q) {
            return "DROP_KEY";
        }

        if (key == GLFW.GLFW_KEY_E) {
            return "INVENTORY_KEY";
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            return "ESCAPE_KEY";
        }

        if (key == GLFW.GLFW_KEY_F) {
            return "OFFHAND_SWAP_KEY";
        }

        return "KEY_"
                + key;
    }

    private record PendingClick(
            long interactionId,
            int button,
            double mouseX,
            double mouseY,

            SlotInfo slotBefore,
            StackInfo carriedBefore,

            boolean shiftDown,
            boolean controlDown,
            boolean altDown
    ) {
    }

    private record PendingDragStep(
            int button,
            double mouseX,
            double mouseY,

            double deltaX,
            double deltaY,

            SlotInfo slotBefore,
            StackInfo carriedBefore,

            boolean shiftDown,
            boolean controlDown,
            boolean altDown
    ) {
    }

    private record PendingRelease(
            int button,
            double mouseX,
            double mouseY,

            SlotInfo slotBefore,
            StackInfo carriedBefore,

            boolean shiftDown,
            boolean controlDown,
            boolean altDown
    ) {
    }

    private static final class DragState {
        private final long interactionId;
        private final int button;
        private final SlotInfo startSlot;

        private int lastSlotId;
        private boolean active;

        private DragState(
                long interactionId,
                int button,
                SlotInfo startSlot
        ) {
            this.interactionId =
                    interactionId;

            this.button =
                    button;

            this.startSlot =
                    startSlot;

            this.lastSlotId =
                    startSlot.slotId();

            this.active =
                    false;
        }

        public long interactionId() {
            return interactionId;
        }

        public int button() {
            return button;
        }

        public SlotInfo startSlot() {
            return startSlot;
        }

        public int lastSlotId() {
            return lastSlotId;
        }

        public boolean active() {
            return active;
        }

        public void activate() {
            active = true;
        }

        public void setLastSlotId(
                int lastSlotId
        ) {
            this.lastSlotId =
                    lastSlotId;
        }
    }

    private record SlotInfo(
            int slotId,
            int slotX,
            int slotY,
            StackInfo stack
    ) {
        private static SlotInfo empty() {
            return new SlotInfo(
                    -1,
                    -1,
                    -1,
                    StackInfo.empty()
            );
        }
    }

    private record StackInfo(
            ItemStackSnapshot snapshot
    ) {
        private static StackInfo empty() {
            return new StackInfo(
                    null
            );
        }
    }
}
