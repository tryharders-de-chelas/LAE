# Results

````
Benchmark                                    Mode  Cnt    Score    Error   Units
YamlParserAccountBenchmark.accountBaseline  thrpt    4  334,537 ± 35,117  ops/ms
YamlParserAccountBenchmark.accountCojen     thrpt    4  317,692 ±  0,406  ops/ms
YamlParserAccountBenchmark.accountReflect   thrpt    4  235,964 ±  1,705  ops/ms
YamlParserStudentBenchmark.studentBaseline  thrpt    4  196,994 ±  1,191  ops/ms
YamlParserStudentBenchmark.studentCojen     thrpt    4  167,265 ±  1,511  ops/ms
YamlParserStudentBenchmark.studentReflect   thrpt    4  117,475 ±  0,322  ops/ms
````

### ACCOUNT
- cojen => 95% of baseline
- reflect => 70% of baseline

### STUDENT
- cojen => 85% of baseline
- reflect => 60% of baseline