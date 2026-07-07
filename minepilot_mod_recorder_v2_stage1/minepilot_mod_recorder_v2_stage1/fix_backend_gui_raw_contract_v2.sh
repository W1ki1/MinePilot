#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="${1:-/opt/ai/mc-ai-bot}"
SCHEMA_FILE="${PROJECT_ROOT}/src/minepilot/common/schema.py"
VALIDATION_FILE="${PROJECT_ROOT}/src/minepilot/common/validation.py"
TEST_FILE="${PROJECT_ROOT}/tests/test_schema_v2.py"
BACKUP_ROOT="${BACKUP_ROOT:-/opt/MinePilot-backedn-backup}"
STAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_DIR="${BACKUP_ROOT}/backend-gui-raw-contract_${STAMP}"

for file in "${SCHEMA_FILE}" "${VALIDATION_FILE}" "${TEST_FILE}"; do
  if [[ ! -f "${file}" ]]; then
    echo "ERROR: missing file: ${file}" >&2
    exit 1
  fi
done

mkdir -p "${BACKUP_DIR}/src/minepilot/common" "${BACKUP_DIR}/tests"
cp -a "${SCHEMA_FILE}" "${BACKUP_DIR}/src/minepilot/common/schema.py"
cp -a "${VALIDATION_FILE}" "${BACKUP_DIR}/src/minepilot/common/validation.py"
cp -a "${TEST_FILE}" "${BACKUP_DIR}/tests/test_schema_v2.py"

python3 - "${SCHEMA_FILE}" "${VALIDATION_FILE}" "${TEST_FILE}" <<'PY'
from pathlib import Path
import sys

schema_path = Path(sys.argv[1])
validation_path = Path(sys.argv[2])
test_path = Path(sys.argv[3])

schema = schema_path.read_text(encoding="utf-8")
validation = validation_path.read_text(encoding="utf-8")
tests = test_path.read_text(encoding="utf-8")

schema = schema.replace(
    "    action_type: GuiActionType\n",
    "    action_type: str\n",
)
schema = schema.replace(
'''            action_type=_enum(
                GuiActionType,
                _required(data, "actionType", path),
                f"{path}.actionType",
            ),
''',
'''            action_type=_string(
                _required(data, "actionType", path),
                f"{path}.actionType",
            ),
''',
)

validation = validation.replace(
'''def _gui_action_requires_slot(action: GuiActionType) -> bool:
    return action in {
        GuiActionType.LEFT_CLICK,
        GuiActionType.RIGHT_CLICK,
        GuiActionType.SHIFT_LEFT_CLICK,
        GuiActionType.SHIFT_RIGHT_CLICK,
        GuiActionType.DRAG_START_LEFT,
        GuiActionType.DRAG_START_RIGHT,
        GuiActionType.DRAG_SLOT_LEFT,
        GuiActionType.DRAG_SLOT_RIGHT,
        GuiActionType.DRAG_END_LEFT,
        GuiActionType.DRAG_END_RIGHT,
        GuiActionType.THROW_ONE,
        GuiActionType.THROW_STACK,
    }
''',
'''def _gui_action_requires_slot(action: str) -> bool:
    return action in {
        GuiActionType.LEFT_CLICK.value,
        GuiActionType.RIGHT_CLICK.value,
        GuiActionType.SHIFT_LEFT_CLICK.value,
        GuiActionType.SHIFT_RIGHT_CLICK.value,
        GuiActionType.DRAG_START_LEFT.value,
        GuiActionType.DRAG_START_RIGHT.value,
        GuiActionType.DRAG_SLOT_LEFT.value,
        GuiActionType.DRAG_SLOT_RIGHT.value,
        GuiActionType.DRAG_END_LEFT.value,
        GuiActionType.DRAG_END_RIGHT.value,
        GuiActionType.THROW_ONE.value,
        GuiActionType.THROW_STACK.value,
        "DRAG_START",
        "DRAG_SLOT",
        "DRAG_END",
    }
''',
)

validation = validation.replace(
'''        outside_action = record.action_type in {
            GuiActionType.LEFT_CLICK_OUTSIDE,
            GuiActionType.RIGHT_CLICK_OUTSIDE,
            GuiActionType.SHIFT_LEFT_CLICK_OUTSIDE,
            GuiActionType.SHIFT_RIGHT_CLICK_OUTSIDE,
        }
''',
'''        outside_action = record.action_type in {
            GuiActionType.LEFT_CLICK_OUTSIDE.value,
            GuiActionType.RIGHT_CLICK_OUTSIDE.value,
            GuiActionType.SHIFT_LEFT_CLICK_OUTSIDE.value,
            GuiActionType.SHIFT_RIGHT_CLICK_OUTSIDE.value,
            "DRAG_OUTSIDE",
        }
''',
)

validation = validation.replace(
    'f"{record.action_type.value} requires a slotId"',
    'f"{record.action_type} requires a slotId"',
)

validation = validation.replace(
'''            if action in {
                GuiActionType.DRAG_START_LEFT,
                GuiActionType.DRAG_START_RIGHT,
            }:
''',
'''            if action in {
                GuiActionType.DRAG_START_LEFT.value,
                GuiActionType.DRAG_START_RIGHT.value,
                "DRAG_START",
            }:
''',
)
validation = validation.replace(
'''            elif action in {
                GuiActionType.DRAG_SLOT_LEFT,
                GuiActionType.DRAG_SLOT_RIGHT,
            }:
''',
'''            elif action in {
                GuiActionType.DRAG_SLOT_LEFT.value,
                GuiActionType.DRAG_SLOT_RIGHT.value,
                "DRAG_SLOT",
                "DRAG_OUTSIDE",
            }:
''',
)
validation = validation.replace(
'''            elif action in {
                GuiActionType.DRAG_END_LEFT,
                GuiActionType.DRAG_END_RIGHT,
            }:
''',
'''            elif action in {
                GuiActionType.DRAG_END_LEFT.value,
                GuiActionType.DRAG_END_RIGHT.value,
                "DRAG_END",
            }:
''',
)

validation = validation.replace(
'''    for name, value in (
        ("pointer.xNormalized", record.pointer.x_normalized),
        ("pointer.yNormalized", record.pointer.y_normalized),
    ):
        if not 0.0 <= value <= 1.0:
''',
'''    pointer_not_applicable = (
        record.pointer.x_pixels < 0.0
        and record.pointer.y_pixels < 0.0
    )

    for name, value in (
        ("pointer.xNormalized", record.pointer.x_normalized),
        ("pointer.yNormalized", record.pointer.y_normalized),
    ):
        if not pointer_not_applicable and not 0.0 <= value <= 1.0:
''',
)

tests = tests.replace(
    "self.assertEqual(action.action_type, GuiActionType.RIGHT_CLICK)",
    'self.assertEqual(action.action_type, "RIGHT_CLICK")',
)

if "def test_raw_gui_action_names_are_forward_compatible" not in tests:
    insertion = '''
    def test_raw_gui_action_names_are_forward_compatible(self) -> None:
        raw = json.loads(
            (FIXTURE / "gui_actions.jsonl")
            .read_text(encoding="utf-8")
            .splitlines()[0]
        )
        raw["actionType"] = "OPEN"
        action = GuiActionRecord.from_dict(raw)
        self.assertEqual(action.action_type, "OPEN")
'''
    marker = "    def test_action_v2_factories(self) -> None:\n"
    tests = tests.replace(marker, insertion + "\n" + marker)

schema_path.write_text(schema, encoding="utf-8")
validation_path.write_text(validation, encoding="utf-8")
test_path.write_text(tests, encoding="utf-8")
PY

cd "${PROJECT_ROOT}"
PYTHONDONTWRITEBYTECODE=1 PYTHONPATH="${PROJECT_ROOT}/src" python3 -m unittest \
  tests.test_schema_v2 -v

find "${PROJECT_ROOT}/src/minepilot/common" "${PROJECT_ROOT}/tests" \
  -type d -name __pycache__ -prune -exec rm -rf {} +

echo
echo "Raw GUI contract compatibility fix applied."
echo "Backup: ${BACKUP_DIR}"
