# MUI

## TODO Fixes

- Remove the Reagent `adapt-react-class` wrapper ns, just require components
  directly everywhere. Use Reagent `:>` but prefer UIx for new code.
- Replace all `:style` uses with `:sx`
- All colors should be used through the MUI theme instead of variables in `mui` ns
- All margin etc., spacing should go through MUI theme spacing
    - Should mostly use only integer values and 0.25 and 0.5?
    - `0.25rem` -> 4px -> 0.5
    - `0.5rem` -> 8px -> 1
    - `1rem` -> 16px -> 2
    - `2rem` -> 32px -> 4
- Replace `Grid` (most if not all) component uses with `Stack`
- Deprecate most of `lipas.ui.components`?
    - Or when the component is useful, use it directly instead of through a wrapper ns
