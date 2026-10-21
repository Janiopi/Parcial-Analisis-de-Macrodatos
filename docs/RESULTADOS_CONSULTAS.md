# Resultados obtenidos por cada consulta

Este documento explica las salidas de las cinco consultas aplicadas al [CSV original](<../3. Reporte Emergencias 01.01 al 20.08.2026 COES-MTC.csv>). Contiene **1362 emergencias** y tiene como fecha de corte el **20/08/2026**. Se tomó la primera ejecución del [clúster](../results/cluster/) para mostrar las cifras. Las salidas finales de las tres repeticiones del clúster y las tres de [un nodo](../results/benchmark/single/timing.tsv) coinciden para cada consulta. Las seis combinaciones de perfil y repetición, con cinco consultas cada una, pasaron el [verificador independiente](../scripts/verify_outputs.py) frente al CSV original.

Este texto se centra en **qué resultados produjeron las consultas y qué significan**. Los tiempos, CPU, memoria y speedup se analizan en [RESULTADOS_BENCHMARK.md](RESULTADOS_BENCHMARK.md).

## 1. Duración promedio por departamento

**Pregunta:** ¿En qué departamentos transcurrieron más días, en promedio, entre la ocurrencia y el cierre de una emergencia?

La consulta calcula la duración de cada caso cerrado y ordena los departamentos por su promedio. Entran **1236 casos con cierre válido**; de los 1362 registros, **122 carecen de fecha de cierre** y **4 tienen un cierre posterior al corte**. La salida solo muestra los diez promedios mayores; no es una tabla de todos los departamentos.

| Puesto | Departamento | Casos cerrados | Promedio (días) |
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

**Interpretación.** Tumbes presenta el promedio más alto, **56,8 días**, pero procede de apenas **5 cierres**. Unos pocos casos prolongados pueden mover mucho esa media. Piura registra **25,92 días** sobre **118 casos**: su posición combina una duración elevada con un volumen considerablemente mayor. La consulta describe los casos cerrados dentro del periodo; las emergencias abiertas no tienen todavía una duración final y quedan fuera de este cálculo.

**Fuente:** [salida de duración del clúster](../results/cluster/duration/result/part-r-00000). Cada fila contiene puesto, departamento, número de casos y promedio en días.

## 2. Porcentaje de interrupción inicial por vía

**Pregunta:** ¿En qué vías fue más frecuente que una emergencia comenzara con estado **INTERRUMPIDO**?

Para cada vía se calcula **100 × casos INTERRUMPIDO / casos totales**. El ranking incluye únicamente vías con **al menos 10 emergencias**. La salida guarda el total y el porcentaje; la columna «Interrumpidos» de esta tabla se obtuvo de los conteos del CSV original y verifica ese porcentaje.

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

**Interpretación.** Los **11 de 11** casos de PE-5NB empezaron con interrupción total, aunque su muestra es pequeña. PE-3S y PE-1SI también registran proporciones altas, con **24 de 36** y **22 de 35** casos. La cifra representa la proporción entre *emergencias registradas* en cada vía; no considera el tráfico, los kilómetros de vía ni la cantidad de viajes expuestos.

**Fuente:** [salida de interrupción del clúster](../results/cluster/interruption/result/part-r-00000).

## 3. Agrupamiento geográfico con DBSCAN

**Pregunta:** ¿Qué concentraciones espaciales de emergencias aparecen al agrupar coordenadas cercanas?

DBSCAN considera vecinos a los puntos separados por **20 km o menos** y exige **4 puntos** para formar un núcleo, contando el propio punto. Procesó las **1362 emergencias**: **1225 quedaron en 64 grupos** y **137 fueron ruido** (grupo -1, aproximadamente **10,1 %**). Entre los puntos agrupados hubo **1162 núcleos** y **63 bordes**. El grupo más pequeño tiene **4** registros, la mediana de tamaño es **8** y el mayor contiene **101**.

| Grupo | Emergencias | Departamentos principales | Provincias principales |
|---:|---:|---|---|
| 13 | 101 | Piura: 89; Cajamarca: 12 | Huancabamba: 45; Ayabaca: 31 |
| 31 | 86 | Pasco: 52; Junín: 31 | Oxapampa: 52; Chanchamayo: 22 |
| 9 | 74 | Cajamarca: 74 | Cutervo: 50; Chota: 17 |
| 3 | 68 | Cusco: 33; Ayacucho: 24; Junín: 11 | La Convención: 33; La Mar: 23 |
| 2 | 61 | Lima: 45; Junín: 16 | Huarochirí: 42; Yauli: 16 |

**Interpretación.** Los grupos más numerosos reúnen emergencias en zonas que pueden atravesar límites administrativos; DBSCAN usa distancia entre coordenadas, no departamentos, para construirlos. El identificador del grupo es una etiqueta técnica y su número no indica gravedad. «Ruido» significa que esos puntos no cumplen los criterios de agrupamiento escogidos; no significa que el registro sea erróneo.

El [índice Davies–Bouldin](../results/cluster/dbscan/davies-bouldin/part-r-00000) fue **0,446716** para los **64 grupos**, excluyendo el ruido. Resume compactación y separación bajo los parámetros usados: un valor menor suele ser favorable al comparar configuraciones, pero esta cifra aislada no prueba que los grupos sean óptimos ni que sean zonas de riesgo causal. Los resultados de ambos perfiles tienen exactamente las mismas asignaciones.

**Fuentes:** [asignaciones de DBSCAN](../results/cluster/dbscan/assignments/) y CSV original para los nombres de departamentos y provincias. Cada asignación contiene ID, latitud, longitud, grupo y tipo (núcleo, borde o ruido).

## 4. Clasificación del estado inicial

**Pregunta:** ¿Puede un MLP distinguir **INTERRUMPIDO** de **RESTRINGIDO** usando ubicación, fecha, evento y departamento?

Se entrenó el modelo durante **40 épocas** y se evaluó en una partición separada, determinada por el ID de emergencia. **INTERRUMPIDO** es la clase positiva. La distribución de clases y las métricas finales fueron:

| Partición | Interrumpido | Restringido | Pérdida de evaluación | Accuracy | Precisión | Recall | F1 |
|---|---:|---:|---:|---:|---:|---:|---:|
| Entrenamiento | 229 | 865 | 0,676617 | 67,1846 % | 36,3445 % | 75,5459 % | 0,490780 |
| Prueba | 57 | 211 | 0,683213 | 59,3284 % | 28,3333 % | 59,6491 % | 0,384181 |

En los **268 casos de prueba**, la matriz de confusión obtenida de las predicciones es:

| Estado real / Predicción | Restringido | Interrumpido |
|---|---:|---:|
| Restringido | 125 | 86 |
| Interrumpido | 23 | 34 |

**Interpretación.** El modelo detectó **34 de los 57** casos inicialmente interrumpidos, pero también anunció interrupción en **86** casos que comenzaron restringidos. Solo **28,33 %** de sus predicciones positivas acertaron. Las probabilidades de prueba estuvieron entre **0,461880 y 0,535632**, cerca del umbral de decisión de 0,5; la separación entre clases es débil. Como referencia para la *accuracy*, predecir siempre **RESTRINGIDO** acertaría **211 de 268 casos (78,73 %)**, frente al **59,33 %** del MLP. Esa referencia tendría recall y F1 nulos para **INTERRUMPIDO**; por eso conviene informar todas las métricas y la matriz, no solo accuracy.

La pérdida **ponderada** registrada en los trabajos de entrenamiento bajó de **1,094959** a **1,070958**. La pérdida de evaluación de la tabla no aplica esa ponderación y no debe compararse directamente con la serie por épocas. La disminución confirma que el objetivo de entrenamiento mejoró, mientras las métricas de prueba muestran que el clasificador aún tiene utilidad predictiva limitada.

**Fuentes:** [métricas](../results/cluster/mlp-class/metrics/part-r-00000), [predicciones](../results/cluster/mlp-class/predictions/) y [épocas de entrenamiento](../results/cluster/mlp-class/).

## 5. Regresión de días hasta el cierre

**Pregunta:** ¿Cuántos días hasta el cierre puede estimar un MLP con la información disponible al inicio?

Solo se usan casos con cierre válido. El objetivo de entrenamiento se transforma como **log(1 + días) / log(162)**; el MAE y el RMSE se calculan después de devolver las predicciones a **días**. Tras **40 épocas**, las métricas fueron:

| Partición | Casos | Pérdida de evaluación | MAE (días) | RMSE (días) |
|---|---:|---:|---:|---:|
| Entrenamiento | 993 | 0,032392 | 14,275179 | 24,478702 |
| Prueba | 243 | 0,027163 | 13,518356 | 24,372828 |

**Interpretación.** En prueba, el error absoluto medio es **13,52 días**. El RMSE, **24,37 días**, es mucho mayor porque penaliza más los errores grandes. Las duraciones reales van de **0 a 139 días**, mientras el MLP predice solo entre **4,73 y 9,43 días**; tiende a acercar todos los casos a una duración corta. Por ejemplo, para la emergencia **817**, el valor real fue **139 días** y el estimado **9,14**, con un error de **129,86 días**. Esta dificultad con los cierres largos explica parte del RMSE elevado.

Como comparación diagnóstica sobre la misma prueba, predecir siempre su **media** (16,54 días) produciría RMSE **22,77 días**, mejor que el MLP, pero MAE **15,52 días**, peor. Predecir siempre la **mediana** (8 días) produciría MAE **13,69 días**, apenas superior al **13,52** del MLP. Estas constantes se calcularon con los valores de prueba para interpretar los errores; no son modelos entrenados válidamente para uso futuro.

La pérdida del entrenamiento por épocas bajó de **0,119695** a **0,032418** en la escala transformada. El descenso muestra que la optimización funcionó, pero la concentración de predicciones indica que el modelo no representa bien la variación de duraciones, especialmente los casos extensos.

**Fuentes:** [métricas](../results/cluster/mlp-reg/metrics/part-r-00000), [predicciones](../results/cluster/mlp-reg/predictions/) y [épocas de entrenamiento](../results/cluster/mlp-reg/).

## Lectura conjunta

Las consultas descriptivas y DBSCAN produjeron salidas reproducibles y verificadas contra el CSV. Los rankings identifican valores altos, pero deben leerse junto con su **número de casos**. El agrupamiento señala proximidad espacial bajo parámetros concretos. Los dos MLP se ejecutaron correctamente y dieron las mismas métricas en ambos perfiles, aunque sus resultados de prueba todavía no respaldan predicciones confiables para casos nuevos.
