# ADL 2 → ADL 1.4 down-converter (fork addition)

Archie upstream converts **ADL 1.4 → ADL 2** (`ADL14Parser` + `ADL14Converter`) but has no built-in reverse path. This fork adds a best-effort down-converter so results can be **benchmarked against Ehrtslib** `ADL14Serializer`.

## Package

`com.nedap.archie.adl14.down`

| Class | Role |
|-------|------|
| `ADL2ToADL14DownConverter` | AOM rewrite + serialize + text shaping |
| `AomIdRewriter` | `idN`→`atNNNN`, expand `ac*` value-sets to `local::…` |
| `IdToAtCodeMapper` | single-code mapping |
| `ADL14DownConversionResult` | ADL 1.4 text + warnings |
| `ADL2ToADL14Cli` (tools) | file CLI |

## CLI

```bash
./gradlew :tools:test --tests com.nedap.archie.adl14.down.ADL2ToADL14DownConverterTest
./gradlew :tools:runADL2ToADL14 -PcliArgs="path/to/file.adls out.adl"
# or:
./scripts/adl2-to-adl14.sh path/to/file.adls out.adl
```

## What works

- Parse ADL 2 with `ADLParser`
- Map `id1` → `at0001` (and specialised `id0.1` → `at0000.1`)
- Expand `ac*` constraints to `[local::at…, …]` when value_sets are present, then drop `value_sets`
- Emit `adl_version=1.4`, shortened HRID (`.v1.0.0` → `.v1`), `concept`, `language`, `specialize`, `ontology`
- Refuse empty definitions (no `undefined[undefined]`)
- Round-trip smoke: down-convert minimal ADL2 → `ADL14Parser` accepts it

## Stubbed / known gaps (compare with Ehrtslib issues #114–#117)

- Full specialised-code / differential semantics (parent flat archetype not consulted)
- Perfect ODIN 1.4 quoting / `items` wrapping for all terminology shapes
- Template / OPT / `use_archetype` → OET path
- Rules section id rewrite
- Slot regex and every Archie-serialized cADL idiom (out of scope for the serializer; parser already accepts them on the way *in*)

## Benchmark vs Ehrtslib

1. Same ADL2 input → Archie down-converter → `.adl`
2. Same ADL2 input → Ehrtslib `parseAdl` + `ADL14Serializer` → `.adl` (after #113 hard-errors on fatals)
3. Both outputs → Archie `ADL14Parser` / Better import

See also: ErikSundvall/ehrtslib#113–#117 and `openehr-genomik-templates/docs/ehrtslib-gaps.md`.
