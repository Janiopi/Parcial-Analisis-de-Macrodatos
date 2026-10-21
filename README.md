# CC531: emergencias viales en Hadoop MapReduce

Implementación en **Java MapReduce nativo** de las cinco consultas del examen. Los algoritmos de agrupamiento y aprendizaje están programados aquí; no se usan bibliotecas de ML ni procesamiento distribuido externo a MapReduce.

La documentación detallada está organizada en el [índice de `docs/`](docs/README.md). Para compilar y ejecutar las consultas, empieza por las [instrucciones de este README](#inicio-rápido-desde-una-copia-del-repositorio) y el [manual de operación de Hadoop](docs/README_Hadoop.md). La [guía de estructura](docs/GUIA_ESTRUCTURA.md) explica el código y la [guía de comparación](docs/GUIA_COMPARACION.md) describe las mediciones.

## Inicio rápido desde una copia del repositorio

```bash
git clone <URL_DEL_REPOSITORIO>
cd <CARPETA_DEL_REPOSITORIO>
bash scripts/build.sh
```

Se necesita JDK 11 y Maven. El script compila y crea los JAR en `dist/`; esa carpeta se genera localmente y no forma parte del repositorio. El CSV de entrada, el código fuente, las guías, los informes y una selección de salidas verificables están incluidos. Las carpetas de compilación, las salidas completas de Hadoop y la configuración del IDE se excluyen de Git.

Los resultados de las cinco consultas están explicados en [RESULTADOS_CONSULTAS.md](docs/RESULTADOS_CONSULTAS.md). La [comparación de rendimiento](docs/RESULTADOS_BENCHMARK.md) incluye los tiempos, recursos y limitaciones de las pruebas con un nodo y con el clúster. Los archivos citados por ambos informes se conservan en [`results/cluster`](results/cluster/) y [`results/benchmark`](results/benchmark/). Las copias completas de `resultados/` y `bench/` permanecen locales.

## Dataset y compilación

Fuente: [Plataforma Nacional de Datos Abiertos, «Emergencias en la Red Vial Nacional» del MTC](https://www.datosabiertos.gob.pe/dataset/emergencias-en-la-red-vial-nacional-ministerio-de-transportes-y-comunicaciones-%E2%80%93-mtc). Publicación: 24/08/2026; corte: 20/08/2026. El CSV incluido tiene 1362 emergencias, 22 columnas, coordenadas decimales, separador `;` y codificación Windows-1252. El grupo confirmó que no se repite en el cuadro de temas. El enunciado en PDF y los archivos de LaTeX se conservan solo en la copia local.

Compilación: JDK 11, Maven y las dependencias Hadoop 3.3.0 declaradas en `pom.xml`. Las VM de [README_Hadoop.md](docs/README_Hadoop.md) ejecutan Hadoop 3.4.2 y Java 11. Compilar con `mvn package`; Maven genera `target/cc531.jar` y un JAR por consulta: `target/cc531-duration.jar`, `target/cc531-interruption.jar`, `target/cc531-dbscan.jar`, `target/cc531-mlp-class.jar` y `target/cc531-mlp-reg.jar`. También se puede usar `bash scripts/build.sh` para copiar los seis JAR a `dist/` y mantener `build/cc531.jar` para los scripts anteriores. Las dependencias Hadoop tienen alcance `provided`: el comando `hadoop jar` aporta esas bibliotecas al ejecutar. Cada ejecución requiere un directorio de salida que todavía no exista y conserva todos los intermedios.

## Consultas, entradas y salidas

```bash
hadoop jar dist/cc531-<consulta>.jar <entrada.csv> <directorio_salida> [epocas]
```

`[epocas]` solo se aplica a `mlp-class` y `mlp-reg` (40 por defecto). El JAR general sigue disponible con `hadoop jar dist/cc531.jar pe.edu.cc531.Main <consulta> <entrada.csv> <directorio_salida> [epocas]`.

| Consulta | Pregunta y método | Salida principal |
|---|---|---|
| `duration` | ¿Qué departamentos tienen mayor promedio de días entre ocurrencia y cierre? Job 1 suma por departamento/evento; job 2 consolida por departamento; job 3 ordena top 10. | `result/part-r-00000`: rango, departamento, cantidad, media decimal de días. |
| `interruption` | ¿Qué vías tienen mayor porcentaje de emergencias inicialmente interrumpidas? Job 1 cuenta por vía/mes; job 2 consolida por vía; job 3 ordena top 10 entre vías con al menos diez casos. | `result/part-r-00000`: rango, vía, cantidad, porcentaje decimal. |
| `dbscan` | ¿Dónde hay concentraciones geográficas de emergencias? DBSCAN con Haversine, radio 20 km y `minPts=4`, contando al propio punto. | `assignments/part-*`: ID, latitud, longitud, ID de grupo, `core`/`border`/`noise`; `davies-bouldin/part-*`: índice y cantidad de grupos. |
| `mlp-class` | ¿Puede predecirse si una emergencia empezó `INTERRUMPIDO` o `RESTRINGIDO`? | `metrics/part-*`, `predictions/part-*`, `epoch-*/part-*` con pérdida y modelo. |
| `mlp-reg` | ¿Cuántos días transcurrirán hasta el cierre? | Mismos archivos, con MAE y RMSE en días. |

Cada consulta decimal ejecuta **tres jobs encadenados**, como exige el enunciado. En duración solo se usan cierres válidos hasta el corte y no anteriores a la ocurrencia: 1236 filas. Cuatro cierres posteriores al corte se excluyen. Las 1362 filas tienen coordenadas válidas.

DBSCAN replica cada punto a nueve celdas espaciales, calcula pares cercanos con Haversine y elimina duplicados. Un job identifica puntos núcleo. Jobs iterativos propagan el menor ID entre núcleos vecinos hasta que el contador de cambios llega a cero. Un punto de borde adyacente a varios grupos recibe el menor ID; el ruido recibe `-1`. Otros tres jobs calculan el índice Davies-Bouldin de los grupos sin ruido mediante centroides, dispersión media y distancias geográficas. Un valor menor suele indicar grupos más compactos y separados.

Los MLP usan latitud, longitud, mes, día del año, evento y departamento. Las cadenas se codifican en dos vectores dispersos de 32 posiciones mediante un hash estable. Arquitectura: 68 entradas, 16 neuronas `tanh`, salida sigmoide para clasificación o lineal para regresión. **Cada época es un job MapReduce**: los mappers suman gradientes por partición y el reducer actualiza los pesos con descenso de gradiente de lote completo (tasa 0.1). Un job previo cuenta las clases del conjunto de entrenamiento y pondera la pérdida positiva por `negativos/positivos`, para evitar que el clasificador prediga solo la clase mayoritaria. La semilla es 42 y hay 40 épocas por defecto; el cuarto argumento permite cambiarlas. La regresión aprende `log(1+días)/log(162)` y evalúa en días. La separación entrenamiento/prueba 80/20 depende del ID y se conserva en ambos entornos. La clasificación usa umbral 0.5. Las métricas por partición son pérdida, accuracy, precisión, recall y F1; o pérdida, MAE y RMSE. Ni `ESTADO_ACTUAL` ni `FECHA_CIERRE` son características de entrada.

## Ejecución en un nodo y en multinodo

En cada VM, verificar Hadoop y los nodos activos:

```bash
hadoop version
java -version
yarn node -list
hdfs dfsadmin -report
```

Las dos configuraciones viven en la **misma VM `master`**. El perfil `cluster` (`/opt/hadoop/etc/hadoop`) usa `worker1@192.168.56.11` y `worker2@192.168.56.12`, con replicación 2; el perfil `single` (`/opt/hadoop/conf-single`) ejecuta DataNode y NodeManager en `master`, con replicación 1. Sus HDFS son independientes. Selecciona `HADOOP_CONF_DIR` y comprueba los nodos activos como indica [README_Hadoop.md](docs/README_Hadoop.md) antes de ejecutar. La configuración del equipo físico no demuestra cuál perfil está activo en la VM.

En el perfil `cluster`, el CSV ya está en HDFS. Ejemplo de ejecución desde `master`:

```bash
hdfs dfs -ls /user/master/cc531/bench-01/input/mtc.csv
hdfs dfs -mkdir -p /user/master/cc531/resultados
hadoop jar laboratorio-mtc/jars/cc531-duration.jar /user/master/cc531/bench-01/input/mtc.csv /user/master/cc531/resultados/duration-01
hadoop jar laboratorio-mtc/jars/cc531-interruption.jar /user/master/cc531/bench-01/input/mtc.csv /user/master/cc531/resultados/interruption-01
hadoop jar laboratorio-mtc/jars/cc531-dbscan.jar /user/master/cc531/bench-01/input/mtc.csv /user/master/cc531/resultados/dbscan-01
hadoop jar laboratorio-mtc/jars/cc531-mlp-class.jar /user/master/cc531/bench-01/input/mtc.csv /user/master/cc531/resultados/mlp-class-01
hadoop jar laboratorio-mtc/jars/cc531-mlp-reg.jar /user/master/cc531/bench-01/input/mtc.csv /user/master/cc531/resultados/mlp-reg-01
hdfs dfs -cat /user/master/cc531/resultados/duration-01/result/part-*
```

Para comparar con un nodo, detén el clúster y arranca el perfil `single` en `master` siguiendo [README_Hadoop.md](docs/README_Hadoop.md). Una prueba en `file:///` con `mapreduce.framework.name=local` sigue siendo útil para desarrollo, pero no representa el perfil `single` de las VM. Las salidas existentes se preservan; elige otra ruta para repetir.

## Medición y evidencia para el informe

La [guía de comparación](docs/GUIA_COMPARACION.md) da los comandos exactos para medir primero `cluster` y después `single`, ambos desde la VM `master`: calentamiento y tres repeticiones por consulta, mismo CSV y JAR, muestras del master y de los workers, verificación de salidas y resumen de medianas. `speedup = tiempo_single / tiempo_cluster`; menos de 1 indica que el clúster tardó más. Con 1362 filas, el arranque de muchos jobs puede dominar el tiempo.

Capturas requeridas por el examen: configuración de red y Hadoop de las VM; `yarn node -list` y `hdfs dfsadmin -report` de cada perfil; UI de YARN durante los jobs; CPU y memoria en master y workers (`htop`); tiempos, salidas y métricas de cada entorno. `bench/comparison.csv`, generado en `master`, es la base de la tabla de tiempo, CPU, memoria y speedup. Extraer `metrics/part-*` de cada MLP y `davies-bouldin/part-*` de DBSCAN para comparar calidad. Las métricas deben ser iguales o próximas entre entornos; el orden de suma puede causar diferencias pequeñas de punto flotante. El informe y la presentación deben mencionar tipo de pregunta, consulta, entradas, salidas, solución, resultados, fuente y fecha del dataset. El examen también pide referencias de investigaciones previas, capturas y exposición máxima de diez minutos.

## Exportación para Power BI

```bash
hdfs dfs -get /user/$USER/cc531/results/dbscan/assignments ./assignments
python3 scripts/export_powerbi.py '3. Reporte Emergencias 01.01 al 20.08.2026 COES-MTC.csv' ./assignments ./powerbi/emergencias_clusters.csv
```

Importar el CSV UTF-8 resultante en Power BI. En un mapa, usar `LATITUD` y `LONGITUD` como coordenadas, `CLUSTER_ID` como leyenda e `ID_EMERGENCIA` como detalle. Mostrar el ruido (`-1`) aparte. Importar también los TSV de `duration/result` e `interruption/result` para barras de promedios y tasas; conservar capturas y el `.pbix` para la entrega completa.

## Verificación local

Con Hadoop 3.3.0, las dos consultas de tres jobs coincidieron con un cálculo independiente sobre el CSV. DBSCAN asignó las 1362 filas: 64 grupos, 1162 puntos núcleo, 63 de borde, 137 de ruido e índice Davies-Bouldin `0.446716`. En 40 épocas, el clasificador ponderado obtuvo F1 de prueba `0.384181` y accuracy `0.593284`; su pérdida ponderada de entrenamiento bajó de `1.094959` a `1.070958`. Es un resultado modesto, que se analiza en los informes. En 40 épocas, la regresión obtuvo MAE de prueba `13.518356` días y RMSE `24.372828`; su pérdida bajó de `0.119695` a `0.032418`. Las salidas locales están en [`results/local`](results/local/README.md); el CSV de mapa se genera con [`scripts/export_powerbi.py`](scripts/export_powerbi.py). Posteriormente se ejecutaron y verificaron las cinco consultas en los perfiles de uno y varios nodos; los resultados y sus límites están documentados en los informes enlazados arriba.

Para comprobar salidas descargadas o pruebas locales, ejecutar `python3 scripts/verify_outputs.py <source.csv> <duration_dir> <interruption_dir> <dbscan_dir> <mlp_class_dir> <mlp_reg_dir>`. El verificador calcula por separado las dos tablas agregadas y DBSCAN con comparación exhaustiva de pares; también comprueba las pérdidas y métricas de los MLP.
