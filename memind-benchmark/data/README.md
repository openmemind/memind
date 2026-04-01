# Benchmark Data

Place benchmark datasets under this directory before running the module.

## Expected layout

```text
memind-benchmark/data/
├── locomo/
│   └── locomo10.json
├── halumem/
│   └── halumem.jsonl
└── longmemeval/
    └── questions.json
```

## Notes

- `LocomoBenchmark` expects `locomo/locomo10.json`
- `HaluMemBenchmark` expects `halumem/halumem.jsonl`
- `LongMemEvalBenchmark` expects `longmemeval/questions.json`
- System properties override the default root with `-Dmemind.benchmark.data-dir=/path/to/data`
