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

public final class GuiInteractionRecorder {
    private static final Map<Screen, PendingClick> PENDING_CLICKS =
            new WeakHashMap<>();

    private GuiInteractionRecorder() {
    }

    public static void register(
            RecordingManager recordingManager
    ) {
        ScreenEvents.AFTER_INIT.register(
                (client, screen, scaledWidth, scaledHeight) -> {
                    if (!(screen instanceof AbstractContainerScreen<?>)) {
                        return;
                    }

                    recordScreenEvent(
                            recordingManager,
                            screen,
                            "SCREEN_OPEN",
                            "OPEN"
                    );

                    /*
                     * Fabric 26.1.2:
                     * beforeMouseClick(Screen, MouseButtonEvent)
                     */
                    ScreenMouseEvents.beforeMouseClick(screen).register(
                            (currentScreen, event) ->
                                    captureBeforeClick(
                                            currentScreen,
                                            event
                                    )
                    );

                    /*
                     * Fabric 26.1.2:
                     * afterMouseClick(
                     *     Screen,
                     *     MouseButtonEvent,
                     *     boolean consumed
                     * ) -> boolean
                     */
                    ScreenMouseEvents.afterMouseClick(screen).register(
                            (currentScreen, event, consumed) -> {
                                recordAfterClick(
                                        recordingManager,
                                        currentScreen,
                                        event
                                );

                                // Nie przechwytujemy kliknięcia.
                                return false;
                            }
                    );

                    ScreenMouseEvents.afterMouseRelease(screen).register(
                            (currentScreen, event, consumed) -> {
                                recordMouseRelease(
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

                    /*
                     * Fabric 26.1.2:
                     * afterKeyPress(Screen, KeyEvent)
                     */
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
                                PENDING_CLICKS.remove(
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

    private static void captureBeforeClick(
            Screen screen,
            Object mouseEvent
    ) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        double mouseX = readDouble(
                mouseEvent,
                -1.0,
                "x",
                "mouseX",
                "getX"
        );

        double mouseY = readDouble(
                mouseEvent,
                -1.0,
                "y",
                "mouseY",
                "getY"
        );

        int button = readInt(
                mouseEvent,
                -1,
                "button",
                "getButton"
        );

        Slot slot = findHoveredSlot(
                containerScreen
        );

        SlotInfo slotInfo = readSlot(
                containerScreen,
                slot
        );

        StackInfo carried = readStack(
                containerScreen
                        .getMenu()
                        .getCarried()
        );

        PENDING_CLICKS.put(
                screen,
                new PendingClick(
                        button,
                        mouseX,
                        mouseY,
                        slotInfo,
                        carried,
                        isShiftDown(),
                        isControlDown(),
                        isAltDown()
                )
        );
    }

    private static void recordAfterClick(
            RecordingManager recordingManager,
            Screen screen,
            Object mouseEvent
    ) {
        if (!recordingManager.isRecording()) {
            PENDING_CLICKS.remove(screen);
            return;
        }

        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        double mouseX = readDouble(
                mouseEvent,
                -1.0,
                "x",
                "mouseX",
                "getX"
        );

        double mouseY = readDouble(
                mouseEvent,
                -1.0,
                "y",
                "mouseY",
                "getY"
        );

        int button = readInt(
                mouseEvent,
                -1,
                "button",
                "getButton"
        );

        PendingClick pending =
                PENDING_CLICKS.remove(screen);

        Slot currentSlot =
                findHoveredSlot(containerScreen);

        SlotInfo slotAfter = readSlot(
                containerScreen,
                currentSlot
        );

        StackInfo carriedAfter = readStack(
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
                        carriedAfter
                )
        );
    }

    private static void recordMouseRelease(
            RecordingManager recordingManager,
            Screen screen,
            Object mouseEvent
    ) {
        if (!recordingManager.isRecording()) {
            return;
        }

        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
            return;
        }

        double mouseX = readDouble(
                mouseEvent,
                -1.0,
                "x",
                "mouseX",
                "getX"
        );

        double mouseY = readDouble(
                mouseEvent,
                -1.0,
                "y",
                "mouseY",
                "getY"
        );

        int button = readInt(
                mouseEvent,
                -1,
                "button",
                "getButton"
        );

        SlotInfo slot = readSlot(
                containerScreen,
                findHoveredSlot(containerScreen)
        );

        StackInfo carried = readStack(
                containerScreen
                        .getMenu()
                        .getCarried()
        );

        recordingManager.recordGuiInteraction(
                createSnapshot(
                        recordingManager,
                        screen,

                        "MOUSE_RELEASE",
                        releaseActionName(button),

                        button,
                        -1,
                        0,

                        mouseX,
                        mouseY,

                        0.0,
                        0.0,

                        slot,

                        isShiftDown(),
                        isControlDown(),
                        isAltDown(),

                        slot.stack(),
                        slot.stack(),

                        carried,
                        carried
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

        SlotInfo slot = readSlot(
                containerScreen,
                findHoveredSlot(containerScreen)
        );

        StackInfo carried = readStack(
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
                        carried
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

        int key = readInt(
                keyEvent,
                -1,
                "key",
                "keyCode",
                "getKey"
        );

        int modifiers = readInt(
                keyEvent,
                0,
                "modifiers",
                "mods",
                "getModifiers"
        );

        SlotInfo slot = readSlot(
                containerScreen,
                findHoveredSlot(containerScreen)
        );

        StackInfo carried = readStack(
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
                        carried
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
                        emptyStack
                )
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
            StackInfo carriedAfter
    ) {
        Minecraft client =
                Minecraft.getInstance();

        int screenWidth = Math.max(
                1,
                client.getWindow()
                        .getGuiScaledWidth()
        );

        int screenHeight = Math.max(
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

        return new GuiInteractionSnapshot(
                recordingManager.getCurrentTick(),
                System.currentTimeMillis(),
                recordingManager.getCurrentFrameName(),

                screen.getClass().getSimpleName(),
                title,

                eventType,
                actionType,

                mouseButton,
                keyCode,
                modifiers,

                mouseX,
                mouseY,
                normalizedX,
                normalizedY,

                scrollHorizontal,
                scrollVertical,

                slot.slotId(),
                slot.slotX(),
                slot.slotY(),

                shiftDown,
                controlDown,
                altDown,

                slotBefore.item(),
                slotBefore.count(),

                slotAfter.item(),
                slotAfter.count(),

                carriedBefore.item(),
                carriedBefore.count(),

                carriedAfter.item(),
                carriedAfter.count()
        );
    }

    /*
     * W oficjalnych mappingach 26.1.2 nie ma publicznego
     * getSlotUnderMouse(). Odczytujemy pole hoveredSlot
     * bez uzależniania się od jego widoczności.
     */
    private static Slot findHoveredSlot(
            AbstractContainerScreen<?> screen
    ) {
        Class<?> currentClass =
                screen.getClass();

        /*
         * Najpierw szukamy pola Slot zawierającego
         * w nazwie "hover".
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

                Slot value = readSlotField(
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
         * Fallback: pierwsze niepuste pole typu Slot.
         */
        currentClass = screen.getClass();

        while (currentClass != null) {
            for (Field field :
                    currentClass.getDeclaredFields()) {

                if (!Slot.class.isAssignableFrom(
                        field.getType()
                )) {
                    continue;
                }

                Slot value = readSlotField(
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

    private static SlotInfo readSlot(
            AbstractContainerScreen<?> screen,
            Slot slot
    ) {
        if (slot == null) {
            return SlotInfo.empty();
        }

        int menuSlotId =
                screen.getMenu()
                        .slots
                        .indexOf(slot);

        return new SlotInfo(
                menuSlotId,
                slot.x,
                slot.y,
                readStack(slot.getItem())
        );
    }

    private static StackInfo readStack(
            ItemStack stack
    ) {
        if (stack == null || stack.isEmpty()) {
            return StackInfo.empty();
        }

        return new StackInfo(
                stack.getItem().toString(),
                stack.getCount()
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
                client.getWindow().handle();

        return GLFW.glfwGetKey(
                window,
                leftKey
        ) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(
                window,
                rightKey
        ) == GLFW.GLFW_PRESS;
    }

    /*
     * MouseButtonEvent i KeyEvent są rekordami/obiektami
     * wejściowymi Minecrafta. Odczyt refleksyjny pozwala
     * uniknąć kolejnej różnicy nazw mappingów.
     */
    private static int readInt(
            Object object,
            int defaultValue,
            String... memberNames
    ) {
        Number number = readNumber(
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
        Number number = readNumber(
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

        for (String memberName : memberNames) {
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
            for (String memberName : memberNames) {
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

        return "MOUSE_RELEASE_" + button;
    }

    private static String inferKeyAction(
            int key
    ) {
        if (key >= GLFW.GLFW_KEY_1
                && key <= GLFW.GLFW_KEY_9) {

            int slotNumber =
                    key - GLFW.GLFW_KEY_1 + 1;

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

        return "KEY_" + key;
    }

    private record PendingClick(
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
            String item,
            int count
    ) {
        private static StackInfo empty() {
            return new StackInfo(
                    "minecraft:air",
                    0
            );
        }
    }
}