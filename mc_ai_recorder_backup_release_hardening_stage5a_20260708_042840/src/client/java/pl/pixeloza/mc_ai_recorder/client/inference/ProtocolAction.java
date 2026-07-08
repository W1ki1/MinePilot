package pl.pixeloza.mc_ai_recorder.client.inference;

public record ProtocolAction(
        int protocolVersion,
        long observationSequenceId,
        String actionType,
        SystemAction system,
        WorldAction world,
        GuiAction gui,
        Integer validForTicks,
        long createdAtMs
) {
    public boolean isSafeIdle() {
        return "SYSTEM".equals(actionType)
                && system != null
                && "SAFE_IDLE".equals(system.action());
    }

    public boolean isWorld() {
        return "WORLD".equals(actionType)
                && world != null;
    }

    public boolean isGui() {
        return "GUI".equals(actionType)
                && gui != null;
    }

    public int effectiveValidForTicks() {
        int value = validForTicks != null
                ? validForTicks
                : 2;

        return Math.max(
                1,
                Math.min(20, value)
        );
    }

    public void validate() {
        if (protocolVersion
                != ProtocolV2.PROTOCOL_VERSION) {
            throw new IllegalArgumentException(
                    "ACTION protocolVersion mismatch"
            );
        }

        if (actionType == null) {
            throw new IllegalArgumentException(
                    "ACTION actionType is missing"
            );
        }

        if ("SYSTEM".equals(actionType)) {
            if (!isSafeIdle()) {
                throw new IllegalArgumentException(
                        "Unsupported SYSTEM action"
                );
            }

            return;
        }

        if ("WORLD".equals(actionType)) {
            if (world == null) {
                throw new IllegalArgumentException(
                        "WORLD action is missing world payload"
                );
            }

            world.validate();
            return;
        }

        if ("GUI".equals(actionType)) {
            if (gui == null) {
                throw new IllegalArgumentException(
                        "GUI action is missing gui payload"
                );
            }

            gui.validate();
            return;
        }

        throw new IllegalArgumentException(
                "Unsupported actionType: "
                        + actionType
        );
    }

    public AiAction toAiAction(
            long timestamp
    ) {
        if (!isWorld()) {
            return null;
        }

        return new AiAction(
                timestamp,
                new AiAction.Buttons(
                        world.forward(),
                        world.back(),
                        world.left(),
                        world.right(),
                        world.jump(),
                        world.sneak(),
                        world.sprint(),
                        world.attack(),
                        world.use(),
                        world.inventoryToggle()
                ),
                new AiAction.Camera(
                        world.yawDeltaDegrees(),
                        world.pitchDeltaDegrees()
                ),
                world.hotbarTarget()
        );
    }

    public String summary() {
        if (isSafeIdle()) {
            return "SAFE_IDLE";
        }

        if (isWorld()) {
            return world.summary();
        }

        if (isGui()) {
            return gui.summary();
        }

        return actionType != null
                ? actionType
                : "INVALID";
    }

    public record SystemAction(
            String action,
            String reason
    ) {
    }

    public record WorldAction(
            boolean forward,
            boolean back,
            boolean left,
            boolean right,
            boolean jump,
            boolean sneak,
            boolean sprint,
            boolean attack,
            boolean use,
            boolean inventoryToggle,
            Integer hotbarTarget,
            float yawDeltaDegrees,
            float pitchDeltaDegrees
    ) {
        public void validate() {
            if (hotbarTarget != null
                    && (
                    hotbarTarget < 0
                            || hotbarTarget > 8
            )) {
                throw new IllegalArgumentException(
                        "hotbarTarget must be null or 0..8"
                );
            }

            if (!Float.isFinite(yawDeltaDegrees)
                    || !Float.isFinite(
                    pitchDeltaDegrees
            )) {
                throw new IllegalArgumentException(
                        "Camera deltas must be finite"
                );
            }

            if (Math.abs(yawDeltaDegrees) > 30.0f
                    || Math.abs(pitchDeltaDegrees) > 30.0f) {
                throw new IllegalArgumentException(
                        "Camera delta exceeds protocol safety range"
                );
            }
        }

        public String summary() {
            StringBuilder result =
                    new StringBuilder("WORLD");

            if (forward) {
                result.append(":F");
            }

            if (back) {
                result.append(":B");
            }

            if (left) {
                result.append(":L");
            }

            if (right) {
                result.append(":R");
            }

            if (jump) {
                result.append(":J");
            }

            if (sprint) {
                result.append(":SPRINT");
            }

            if (attack) {
                result.append(":ATK");
            }

            if (use) {
                result.append(":USE");
            }

            if (yawDeltaDegrees != 0.0f) {
                result.append(":YAW");
            }

            if (pitchDeltaDegrees != 0.0f) {
                result.append(":PITCH");
            }

            return result.toString();
        }
    }

    public record GuiAction(
            String operation,
            String screenType,
            String menuType,
            Integer slotId,
            Integer button,
            String clickType,
            Integer buttonId,
            Integer quickCraftButton,
            String expectedContainerFingerprint
    ) {
        public void validate() {
            if (operation == null
                    || screenType == null
                    || menuType == null
                    || screenType.isBlank()
                    || menuType.isBlank()) {
                throw new IllegalArgumentException(
                        "GUI action requires operation and context"
                );
            }

            switch (operation) {
                case "CLICK_SLOT" -> {
                    validateSlot();
                    validateButton();
                    validateClickType();
                }

                case "CLICK_OUTSIDE" -> {
                    if (slotId != null
                            && slotId != -999) {
                        throw new IllegalArgumentException(
                                "CLICK_OUTSIDE slotId must be -999 or null"
                        );
                    }

                    validateButton();
                    validateClickType();
                }

                case "QUICK_CRAFT_START" ->
                        validateQuickCraftButton(0);

                case "QUICK_CRAFT_ADD_SLOT" -> {
                    validateSlot();
                    validateQuickCraftButton(1);
                }

                case "QUICK_CRAFT_END" ->
                        validateQuickCraftButton(2);

                case "BUTTON_CLICK" -> {
                    if (buttonId == null
                            || buttonId < 0
                            || buttonId > 255) {
                        throw new IllegalArgumentException(
                                "buttonId must be 0..255"
                        );
                    }
                }

                case "CLOSE_SCREEN" -> {
                }

                default -> throw new IllegalArgumentException(
                        "Unsupported GUI operation: "
                                + operation
                );
            }
        }

        private void validateSlot() {
            if (slotId == null
                    || slotId < 0
                    || slotId > 255) {
                throw new IllegalArgumentException(
                        "slotId must be 0..255"
                );
            }
        }

        private void validateButton() {
            if (button == null
                    || button < 0
                    || button > 8) {
                throw new IllegalArgumentException(
                        "button must be 0..8"
                );
            }
        }

        private void validateQuickCraftButton(
                int expectedHeader
        ) {
            if (quickCraftButton == null
                    || quickCraftButton < 0
                    || quickCraftButton > 6) {
                throw new IllegalArgumentException(
                        "quickCraftButton must be 0..6"
                );
            }

            int actualHeader =
                    quickCraftButton & 3;

            int quickCraftType =
                    quickCraftButton >> 2 & 3;

            if (actualHeader != expectedHeader) {
                throw new IllegalArgumentException(
                        "quickCraftButton header does not match operation"
                );
            }

            if (quickCraftType < 0
                    || quickCraftType > 1) {
                throw new IllegalArgumentException(
                        "Only survival quick-craft types 0 and 1 are allowed"
                );
            }
        }

        private void validateClickType() {
            if (clickType == null) {
                throw new IllegalArgumentException(
                        "clickType is required"
                );
            }

            switch (clickType) {
                case "PICKUP",
                     "QUICK_MOVE",
                     "SWAP",
                     "CLONE",
                     "THROW",
                     "PICKUP_ALL" -> {
                }

                default -> throw new IllegalArgumentException(
                        "Unsupported clickType: "
                                + clickType
                );
            }
        }

        public String summary() {
            StringBuilder result =
                    new StringBuilder("GUI:")
                            .append(operation);

            if (clickType != null) {
                result.append(':')
                        .append(clickType);
            }

            if (slotId != null) {
                result.append(":S")
                        .append(slotId);
            }

            return result.toString();
        }
    }
}
