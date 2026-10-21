# Resultados de las consultas y comparación de rendimiento

Este informe analiza la campaña local de `bench/` y `resultados/`. Una selección de sus [mediciones](results/benchmark/) y [salidas finales](results/cluster/) se conserva en el repositorio; las copias completas permanecen fuera de Git. La entrada fue el archivo [`3. Reporte Emergencias 01.01 al 20.08.2026 COES-MTC.csv`](<3. Reporte Emergencias 01.01 al 20.08.2026 COES-MTC.csv>), con **1362 emergencias** y fecha de corte **20/08/2026**. Las cinco consultas se ejecutaron con el mismo código y parámetros en dos perfiles Hadoop: un nodo (`master`) y un clúster (`master`, `worker1` y `worker2`). El perfil de un nodo usa replicación HDFS 1; el clúster, replicación 2. Por ello, los tiempos comparan las configuraciones operativas completas y no aíslan exclusivamente el efecto del paralelismo.

## Cómo se midió y comprobó

Cada consulta tuvo un calentamiento sin cronometrar y tres repeticiones medidas. Los tiempos de [`results/benchmark/single/timing.tsv`](results/benchmark/single/timing.tsv) y [`results/benchmark/cluster/timing.tsv`](results/benchmark/cluster/timing.tsv) abarcan la consulta completa, incluidos sus trabajos MapReduce. Se usa la **mediana** de las tres repeticiones y se calcula `speedup = mediana_un_nodo / mediana_clúster`: un valor mayor que 1 indica menor tiempo en el clúster. La guía de [comparación](GUIA_COMPARACION.md) y el [script del benchmark](scripts/benchmark.sh) describen el procedimiento.

Las muestras de recursos provienen de [`sample-node.sh`](scripts/sample-node.sh): CPU ocupada de la VM y memoria usada calculada como `MemTotal − MemAvailable`, en **MiB**. Para cada repetición se promedia cada nodo durante la ejecución; luego se promedian los nodos y las tres repeticiones. La memoria total suma la memoria media de las VM activas. Son medidas de las **VM completas**, incluidos Hadoop y el sistema operativo; no representan solo el consumo de una consulta. El porcentaje de CPU del clúster es el promedio entre nodos, no la suma de sus porcentajes.

Se ejecutó [`verify_outputs.py`](scripts/verify_outputs.py) con el CSV original para las **seis combinaciones** de perfil y repetición. Todas pasaron: los diez primeros resultados de duración e interrupción coinciden con agregaciones independientes; las 1362 asignaciones de DBSCAN coinciden con una referencia que compara todos los pares de puntos; ambos MLP completaron 40 épocas y redujeron su pérdida. Además, las salidas finales de cada consulta son iguales entre repeticiones y perfiles, y las métricas de prueba se contrastaron con los archivos de predicciones. Esta comprobación respalda la corrección de las salidas calculadas, sin convertir una métrica predictiva baja en un buen modelo.

## Rendimiento

Los tiempos están en segundos. Las columnas `1 / 2 / 3` son las tres repeticiones medidas; la reducción es `(1 − mediana_clúster / mediana_un_nodo) × 100`.

| Consulta | Un nodo: 1 / 2 / 3 | Mediana un nodo | Clúster: 1 / 2 / 3 | Mediana clúster | Speedup | Reducción de tiempo |
|---|---:|---:|---:|---:|---:|---:|
| Duración | 128,793 / 126,367 / 130,836 | 128,793 | 93,475 / 101,782 / 90,259 | 93,475 | 1,378× | 27,4 % |
| Interrupción | 131,938 / 126,506 / 120,742 | 126,506 | 92,654 / 98,599 / 100,786 | 98,599 | 1,283× | 22,1 % |
| DBSCAN | 550,769 / 553,152 / 577,645 | 553,152 | 483,922 / 494,933 / 484,625 | 484,625 | 1,141× | 12,4 % |
| MLP clasificación | **25 104,188** / 2531,292 / 2516,170 | 2531,292 | 1711,524 / 1706,721 / 1705,606 | 1706,721 | 1,483× | 32,6 % |
| MLP regresión | 2452,002 / 2486,667 / 2438,715 | 2452,002 | 1668,972 / 1669,284 / 1903,514 | 1669,284 | 1,469× | 31,9 % |

El clúster terminó antes en las cinco consultas. La menor mejora corresponde a DBSCAN (12,4 %); la mayor, a clasificación (32,6 %). Duración e interrupción requieren tres trabajos cada una; DBSCAN ejecutó 16 y los MLP 43 y 42, respectivamente, incluyendo sus etapas auxiliares y 40 épocas. Con solo 1362 filas, el inicio y la coordinación de tantos trabajos son una parte importante del tiempo total. Estos resultados muestran una ventaja del perfil de clúster en esta campaña, pero no permiten estimar cómo escalaría con otros tamaños de datos o con hardware físico independiente.

La primera repetición de clasificación en un nodo es **anómala**: duró 25 104,188 s, casi diez veces lo observado en las otras dos. El [resumen de su log](results/benchmark/single/mlp-class-1-gap.txt) registra un intervalo de aproximadamente **22 573 s (6 h 16 min)** sin mensajes de progreso; el [muestreo del nodo](results/benchmark/single/mlp-class-1-node.tsv) presenta un salto temporal equivalente. Los trabajos terminaron correctamente, pero los archivos no identifican la causa del intervalo. La mediana evita que esta observación determine el speedup. El promedio de CPU de esa repetición solo representa los instantes efectivamente muestreados, no todo su tiempo de pared. Al citar los tiempos, conviene mostrar también las tres repeticiones y esta incidencia.

### CPU y memoria

| Consulta | CPU un nodo | CPU media por nodo del clúster | RAM un nodo (MiB) | RAM media por nodo del clúster (MiB) | RAM total clúster (MiB) |
|---|---:|---:|---:|---:|---:|
| Duración | 73,2 % | 37,4 % | 1554,9 | 992,2 | 2976,7 |
| Interrupción | 73,1 % | 35,4 % | 1550,1 | 1005,5 | 3016,4 |
| DBSCAN | 68,4 % | 32,2 % | 1512,9 | 1009,2 | 3027,6 |
| MLP clasificación | 72,9 % | 43,2 % | 1552,6 | 1067,6 | 3202,8 |
| MLP regresión | 73,0 % | 43,9 % | 1554,2 | 1064,0 | 3192,1 |

En un nodo, la CPU media se situó entre 68,4 % y 73,2 %. En el clúster, el promedio **por VM** fue menor, entre 32,2 % y 43,9 %, mientras que la memoria **sumada de las tres VM** fue cercana al doble de la memoria usada por la VM única. No debe interpretarse la CPU media menor como menor trabajo total: se distribuye entre nodos y las tareas alternan periodos de cálculo, comunicación y espera. Estas mediciones tampoco demuestran por sí solas qué tareas procesó cada worker; para ello se requieren los registros de YARN o JobHistory.

## Resultados de las consultas descriptivas

### Duración promedio hasta el cierre

La consulta usa emergencias con cierre válido, no anterior a la ocurrencia y hasta el corte. Del CSV, **1236** cumplen estas condiciones; **122** carecen de fecha de cierre y **4** tienen cierre posterior al corte. El promedio se expresa en días. La [salida verificada](results/cluster/duration/result/part-r-00000) contiene los diez departamentos con mayor promedio:

| Puesto | Departamento | Casos cerrados válidos | Promedio (días) |
|---:|---|---:|---:|
| 1 | Tumbes | 5 | 56,8000 |
| 2 | Moquegua | 22 | 26,4545 |
| 3 | Áncash | 48 | 26,2708 |
| 4 | Piura | 118 | 25,9237 |
| 5 | La Libertad | 36 | 24,3056 |
| 6 | Ica | 14 | 24,0714 |
| 7 | Cusco | 77 | 20,4675 |
| 8 | Huancavelica | 49 | 19,4694 |
| 9 | Junín | 67 | 18,3731 |
| 10 | Ucayali | 28 | 16,8571 |

Tumbes tiene el mayor promedio, pero se basa en **solo cinco cierres**; es sensible a unos pocos casos prolongados. Piura combina un promedio alto con **118 casos**, por lo que representa un volumen mucho mayor en este ranking. Los promedios describen únicamente emergencias cerradas dentro del periodo: no estiman el tiempo final de las emergencias aún abiertas ni deben leerse como una comparación causal entre departamentos.

### Porcentaje de interrupción inicial por vía

La consulta calcula `100 × casos INTERRUMPIDO / casos totales` y ordena las vías con **al menos diez casos**. La [salida verificada](results/cluster/interruption/result/part-r-00000) muestra:

| Puesto | Vía | Casos | Interrumpidos | Porcentaje |
|---:|---|---:|---:|---:|
| 1 | PE-5NB | 11 | 11 | 100,0000 % |
| 2 | PE-3S | 36 | 24 | 66,6667 % |
| 3 | PE-1SI | 35 | 22 | 62,8571 % |
| 4 | PE-1S | 26 | 15 | 57,6923 % |
| 5 | PE-30C | 24 | 10 | 41,6667 % |
| 6 | PE-14A | 22 | 8 | 36,3636 % |
| 7 | PE-28D | 11 | 4 | 36,3636 % |
| 8 | PE-32 | 22 | 8 | 36,3636 % |
| 9 | PE-22 | 57 | 19 | 33,3333 % |
| 10 | PE-08 | 16 | 4 | 25,0000 % |

PE-5NB aparece con 100 %, aunque la evidencia consiste en **11 casos**. PE-3S y PE-1SI mantienen porcentajes altos sobre 36 y 35 casos, respectivamente. El resultado se refiere al **estado inicial de las emergencias registradas**: no mide la probabilidad de interrupción de un viaje ni incorpora el volumen de tráfico o la exposición de cada vía.

## Agrupamiento geográfico: DBSCAN

DBSCAN usa distancia Haversine, radio de **20 km** y `minPts = 4`, contando el propio punto. La [tabla de asignaciones](results/cluster/dbscan/assignments/) cubre las 1362 emergencias: **64 grupos**, **1162 puntos núcleo**, **63 de borde** y **137 de ruido**. El ruido representa **10,1 %** de los registros. El grupo más grande contiene 101 emergencias. La etiqueta de cada grupo es un identificador derivado de los puntos, no una categoría geográfica oficial.

El [índice Davies–Bouldin](results/cluster/dbscan/davies-bouldin/part-r-00000) es **0,446716**; su valor se reprodujo a partir de los centroides y dispersiones guardados. Un valor menor suele expresar grupos más compactos y separados bajo esa definición, pero **0,446716 no tiene un umbral universal de calidad**. El índice excluye los puntos de ruido y depende de los parámetros elegidos. Las asignaciones y el índice son idénticos en ambos perfiles; esa coincidencia verifica reproducibilidad, no demuestra que 20 km y `minPts = 4` sean la mejor configuración. Para describir ubicaciones concretas de concentración se necesita inspeccionar el mapa o las coordenadas, no solo el índice.

## Modelos predictivos

Ambos MLP usan coordenadas, fecha, evento y departamento como entradas; la separación entrenamiento/prueba depende del ID y permanece igual entre perfiles. Se entrenaron **40 épocas** con pesos iniciales deterministas. Las tablas muestran **entrenamiento y prueba**; todos sus valores son idénticos en el clúster y en un nodo. La pérdida se calcula en la escala y con la función propia de cada modelo, por lo que no es comparable directamente entre clasificación y regresión.

### Clasificación del estado inicial

| Partición | Registros | Pérdida | Accuracy | Precisión | Recall | F1 |
|---|---:|---:|---:|---:|---:|---:|
| Entrenamiento | 1094 | 0,676617 | 67,1846 % | 36,3445 % | 75,5459 % | 0,490780 |
| Prueba | 268 | 0,683213 | 59,3284 % | 28,3333 % | 59,6491 % | 0,384181 |

La [métrica](results/cluster/mlp-class/metrics/part-r-00000) concuerda con las [predicciones](results/cluster/mlp-class/predictions/): en prueba hay **34 verdaderos positivos, 86 falsos positivos, 23 falsos negativos y 125 verdaderos negativos**, tomando `INTERRUMPIDO` como clase positiva y umbral 0,5. El conjunto de prueba contiene **57 positivos y 211 negativos**. Por ello, predecir siempre la clase mayoritaria (`RESTRINGIDO`) obtendría **78,73 % de accuracy**, superior al **59,33 %** del MLP. La ponderación de la clase positiva favoreció el recall, pero produjo muchos falsos positivos; las probabilidades previstas en prueba quedaron entre **0,461880 y 0,535632**, muy cerca del umbral. El F1 de **0,384181** y la comparación con la referencia simple indican capacidad predictiva limitada.

La pérdida registrada durante el entrenamiento bajó de **1,094959** a **1,070958**. Esa serie corresponde a la **pérdida ponderada de entrenamiento** en los jobs de gradiente; la pérdida `train` de la tabla es la evaluación final sin esa ponderación. Son magnitudes distintas y no deben confrontarse como si fueran dos puntos de una misma curva. La mejora del objetivo de entrenamiento confirma que el algoritmo actualizó los pesos, pero no que generalice bien.

### Regresión de días hasta el cierre

| Partición | Registros | Pérdida | MAE (días) | RMSE (días) |
|---|---:|---:|---:|---:|
| Entrenamiento | 993 | 0,032392 | 14,275179 | 24,478702 |
| Prueba | 243 | 0,027163 | 13,518356 | 24,372828 |

El [MAE](results/cluster/mlp-reg/metrics/part-r-00000) significa un error absoluto medio de **13,52 días** en prueba. El RMSE de **24,37 días**, bastante mayor, muestra que algunos errores grandes tienen peso importante. En las [predicciones de prueba](results/cluster/mlp-reg/predictions/) los cierres reales abarcan **0 a 139 días**, pero las estimaciones solo **4,73 a 9,43 días**. El modelo comprime las predicciones y subestima los cierres largos.

Como referencia calculada sobre el mismo conjunto de prueba, predecir siempre su **media** (16,54 días) daría **RMSE 22,77 días**, menor que el del MLP, aunque su MAE sería **15,52 días**, mayor. Predecir siempre la **mediana** (8 días) daría **MAE 13,69 días**, muy cercano al **13,52** del MLP. Estas referencias usan el conjunto de prueba solo para diagnosticar las métricas; no son modelos entrenados válidamente para desplegar. La pérdida de entrenamiento por época bajó de **0,119695** a **0,032418** en la escala transformada `log(1 + días) / log(162)`. La pérdida final de la tabla también está en esa escala; MAE y RMSE sí se expresan en días.

## Conclusión

Las cinco consultas terminaron correctamente, las salidas contrastadas coinciden con el CSV original y el clúster redujo la mediana de tiempo entre **12,4 % y 32,6 %** en esta campaña. Los resultados descriptivos y el agrupamiento son reproducibles entre perfiles. En cambio, los dos MLP todavía ofrecen una calidad predictiva limitada frente a referencias simples: el clasificador genera demasiados falsos positivos y la regresión no representa bien los cierres largos. La repetición anómala de clasificación en un nodo debe acompañar cualquier tabla de rendimiento publicada.
