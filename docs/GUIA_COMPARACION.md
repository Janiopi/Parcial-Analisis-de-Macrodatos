# Comparar las consultas: master solo y clúster

**Las dos mediciones se ejecutan en la VM `master`**, como usuario `master`. En modo `cluster`, `master` coordina y `worker1` y `worker2` procesan tareas. En modo `single`, la misma VM `master` ejecuta también DataNode y NodeManager; los workers están apagados. Ambos modos usan **HDFS y YARN**. Pop!_OS solo sirve para preparar y transferir archivos.

Los perfiles están documentados en [README_Hadoop.md](README_Hadoop.md): `cluster` usa `/opt/hadoop/etc/hadoop` y `single` usa `/opt/hadoop/conf-single`. Sus HDFS son independientes aunque ambos se llamen `hdfs://master:9000`. Hay que cargar el mismo CSV en cada perfil y guardar los resultados del clúster antes de cambiar a `single`.

## 1. Qué se compara

| Aspecto | Clúster | Un nodo |
|---|---|---|
| VM encendidas | master, worker1 y worker2 | Solo master |
| NodeManagers | worker1 y worker2 | master |
| DataNodes | worker1 y worker2 | master |
| Replicación HDFS | 2 | 1 |
| Hadoop de las VM | 3.4.2 | 3.4.2 |

Se usa el **mismo JAR, CSV, código y parámetros**. Cada consulta tiene un calentamiento sin medir y **tres repeticiones medidas**. `mlp-class` y `mlp-reg` usan 40 épocas en ambos modos. La replicación conserva el valor real de cada perfil; el rendimiento refleja estas dos configuraciones operativas completas.

Comprueba que los límites de memoria/vCPU por tarea y por NodeManager sigan los ajustes previstos en [README_Hadoop.md](README_Hadoop.md). Como `cluster` usa replicación 2 y más VM, el speedup medido refleja también esos costos; no atribuyas toda la diferencia solo al paralelismo.

El script registra el tiempo de la consulta completa, CPU y memoria de cada VM activa. La tabla final usa la **mediana** de los tiempos y calcula `speedup = tiempo_single / tiempo_cluster`. El CSV tiene 1362 filas: iniciar muchos jobs puede costar más que el cálculo, así que un speedup menor que 1 es posible.

## 2. Preparar archivos una sola vez

Si el JAR y los scripts aún no están en `master`, cópialos desde el proyecto en Pop!_OS. Estos comandos **no ejecutan consultas**:

```bash
cd /home/janiopi/IdeaProjects/Parcial
bash scripts/build.sh
ssh master@192.168.56.10 'mkdir -p /home/master/cc531-bench/dist /home/master/cc531-bench/scripts'
scp dist/cc531.jar master@192.168.56.10:/home/master/cc531-bench/dist/
scp scripts/benchmark.sh scripts/sample-node.sh scripts/summarize_benchmark.py scripts/verify_outputs.py master@192.168.56.10:/home/master/cc531-bench/scripts/
ssh master@192.168.56.10
```

Desde aquí, todos los comandos se ejecutan **dentro de `master`**. El JAR se compiló con dependencias Hadoop `provided`; las VM lo ejecutan con Hadoop 3.4.2. YARN distribuye el JAR a los workers, por lo que basta dejarlo en `master`.

```bash
cd /home/master/cc531-bench
sha256sum dist/cc531.jar
```

Conserva este hash. Si ya tienes los archivos en `master`, verifica su ubicación y continúa.

## 3. Medir el clúster

Enciende las tres VM. Si sus servicios no están arrancados, sigue el [arranque multinodo](README_Hadoop.md#4-arrancar-multinodo-desde-los-servicios-detenidos). En `master`, selecciona el perfil y comprueba los servicios:

```bash
export HADOOP_CONF_DIR=/opt/hadoop/etc/hadoop
echo "$HADOOP_CONF_DIR"
hdfs getconf -confKey dfs.namenode.name.dir
hdfs getconf -confKey dfs.replication
hdfs getconf -confKey mapreduce.framework.name
hdfs dfsadmin -report
yarn node -list
ssh -o BatchMode=yes worker1 hostname
ssh -o BatchMode=yes worker2 hostname
```

Espera `/opt/hadoop/runtime/cluster/name`, replicación `2`, `mapreduce.framework.name=yarn`, **dos DataNodes vivos** y **dos NodeManagers RUNNING** (`worker1` y `worker2`). SSH debe funcionar sin pedir contraseña para medir CPU y memoria remotas. Seleccionar el perfil en la terminal no cambia los servicios ya arrancados.

El CSV existente del clúster está en `/user/master/cc531/bench-01/input/mtc.csv`. Descárgalo al **disco local de `master`** para subirlo después al HDFS de `single`:

```bash
cd /home/master/cc531-bench
hdfs dfs -ls /user/master/cc531/bench-01/input/mtc.csv
hdfs dfs -get /user/master/cc531/bench-01/input/mtc.csv /home/master/cc531-bench/mtc.csv
hdfs dfs -mkdir -p /user/master/cc531/bench-01/salidas
sha256sum mtc.csv
hdfs dfs -cat /user/master/cc531/bench-01/input/mtc.csv | sha256sum
hdfs dfs -stat 'bytes=%b replicas=%r' /user/master/cc531/bench-01/input/mtc.csv
```

Los hashes del CSV deben coincidir y la entrada HDFS debe tener replicación `2`. `-get` copia de HDFS al disco de `master`. Si `mtc.csv` ya existe en el disco local, omite `-get` y compara el hash. Para una campaña nueva, usa otro nombre, por ejemplo `bench-02`, en ambos perfiles.

Ejecuta las cinco consultas:

```bash
cd /home/master/cc531-bench
bash scripts/benchmark.sh cluster /user/master/cc531/bench-01/input/mtc.csv /user/master/cc531/bench-01/salidas 3 worker1 worker2
```

`cluster` etiqueta la medición; **no activa el perfil Hadoop**. El `3` indica repeticiones medidas, además del calentamiento. `worker1 worker2` permite tomar muestras por SSH. Los tiempos y logs quedan en `bench/cluster/`; los resultados originales, en el HDFS del clúster bajo `/user/master/cc531/bench-01/salidas/cluster/`.

Antes de detener el clúster, descarga sus salidas al disco local de `master`:

```bash
hdfs dfs -get /user/master/cc531/bench-01/salidas/cluster /home/master/cc531-bench/resultados-cluster
```

## 4. Cambiar a `single` y medirlo

Termina las consultas del clúster. **Detén sus servicios con el perfil de clúster todavía seleccionado**:

```bash
export HADOOP_CONF_DIR=/opt/hadoop/etc/hadoop
mapred --daemon stop historyserver
stop-yarn.sh
stop-dfs.sh
jps
ssh worker1 '/usr/lib/jvm/java-11-openjdk-amd64/bin/jps'
ssh worker2 '/usr/lib/jvm/java-11-openjdk-amd64/bin/jps'
```

Comprueba que desaparecieron los servicios Hadoop en las tres VM. Después apaga `worker1` y `worker2` desde sus terminales o desde VirtualBox; deja encendida solo `master`. [README_Hadoop.md](README_Hadoop.md#7-detener-single-y-alternar-entre-modos) explica las comprobaciones de esta transición. **No formatees ninguno de los HDFS.**

En `master`, activa `single` y arranca sus servicios:

```bash
export HADOOP_CONF_DIR=/opt/hadoop/conf-single
hdfs getconf -confKey dfs.namenode.name.dir
hdfs getconf -confKey dfs.replication
start-dfs.sh
start-yarn.sh
mapred --daemon start historyserver
jps
hdfs getconf -confKey mapreduce.framework.name
hdfs dfsadmin -report
yarn node -list
```

Espera `/opt/hadoop/runtime/single/name`, replicación `1`, **un DataNode** y **un NodeManager RUNNING**, ambos en `master`. El perfil también debe usar YARN. Si abres otra terminal o ejecutas `source ~/.bashrc`, selecciona y comprueba otra vez `HADOOP_CONF_DIR`.

Sube el CSV local al HDFS independiente de `single`. La ruta tiene el mismo nombre que en el clúster, pero pertenece a otro HDFS:

```bash
cd /home/master/cc531-bench
hdfs dfs -mkdir -p /user/master/cc531/bench-01/input /user/master/cc531/bench-01/salidas
hdfs dfs -put /home/master/cc531-bench/mtc.csv /user/master/cc531/bench-01/input/mtc.csv
hdfs dfs -setrep -w 1 /user/master/cc531/bench-01/input/mtc.csv
hdfs dfs -cat /user/master/cc531/bench-01/input/mtc.csv | sha256sum
hdfs dfs -stat 'bytes=%b replicas=%r' /user/master/cc531/bench-01/input/mtc.csv
```

El hash debe ser igual al del clúster y la replicación debe ser `1`. Si el archivo ya existe en el HDFS de `single`, omite `-put` y compara su hash.

Ejecuta el mismo trabajo con el mismo JAR:

```bash
cd /home/master/cc531-bench
bash scripts/benchmark.sh single /user/master/cc531/bench-01/input/mtc.csv /user/master/cc531/bench-01/salidas 3
hdfs dfs -get /user/master/cc531/bench-01/salidas/single /home/master/cc531-bench/resultados-single
```

Los tiempos y logs quedan en `bench/single/`. Las salidas originales siguen en el HDFS de `single`; la copia descargada está en `resultados-single/`. La copia del clúster sigue disponible en `resultados-cluster/` del mismo disco local de `master`.

## 5. Resumir tiempo, CPU y RAM

Todavía en `master`, desde `/home/master/cc531-bench`:

```bash
python3 scripts/summarize_benchmark.py bench bench/comparison.csv
cat bench/comparison.csv
```

Una fila representa cada consulta. Lee las columnas así:

| Columna | Significado |
|---|---|
| `n_single`, `n_cluster` | Repeticiones exitosas; espera `3` y `3`. |
| `nodos_muestreados_single`, `nodos_muestreados_cluster` | Menor número de nodos con muestras en alguna repetición; espera `1` y `3`. |
| `segundos_single`, `segundos_cluster` | **Mediana** del tiempo total de las tres repeticiones. |
| `cpu_single_pct`, `cpu_cluster_pct` | CPU media por nodo, promediada entre nodos y repeticiones. |
| `memoria_*_mb` | Memoria media por nodo. |
| `memoria_total_*_mb` | Suma de la memoria media de las VM activas. |
| `speedup` | `segundos_single / segundos_cluster`; mayor que `1` favorece al clúster. |

`bench/cluster/timing.tsv` y `bench/single/timing.tsv` conservan **cada tiempo individual**. Los archivos `*-node.tsv`, `*-worker1.tsv` y `*-worker2.tsv` contienen muestras de CPU y RAM; los `.log` conservan la salida de Hadoop. CPU y RAM corresponden a las VM completas, incluidos servicios y sistema operativo. El porcentaje de CPU del clúster promedia nodos; no suma porcentajes. Con dos NodeManagers de cálculo en `cluster`, puedes informar también `eficiencia = speedup / 2`.

Las columnas con sufijo `_mb` usan en realidad **MiB** (`kB / 1024`). Si repites toda la campaña, prepara una carpeta local nueva en `master` (por ejemplo `/home/master/cc531-bench-02`) y rutas HDFS nuevas (`bench-02` en cada perfil). El script protege `timing.tsv` existente para no sobrescribir mediciones anteriores.

## 6. Comprobar resultados

Las dos copias descargadas están en el disco local de `master`. El verificador del proyecto revisa los rankings de duración e interrupción, las asignaciones de DBSCAN y la evolución de pérdida de ambos MLP. Ejecútalo una vez por modo:

```bash
python3 scripts/verify_outputs.py mtc.csv \
  resultados-cluster/duration/run-1 \
  resultados-cluster/interruption/run-1 \
  resultados-cluster/dbscan/run-1 \
  resultados-cluster/mlp-class/run-1 \
  resultados-cluster/mlp-reg/run-1

python3 scripts/verify_outputs.py mtc.csv \
  resultados-single/duration/run-1 \
  resultados-single/interruption/run-1 \
  resultados-single/dbscan/run-1 \
  resultados-single/mlp-class/run-1 \
  resultados-single/mlp-reg/run-1
```

Para la tabla de calidad del informe, consulta en ambos modos `result/part-*` de duración e interrupción, `davies-bouldin/part-*` de DBSCAN y `metrics/part-*` de los MLP. Compara F1 y accuracy de clasificación, y MAE y RMSE de regresión, en las filas `test`. Pueden existir diferencias decimales pequeñas por el orden de suma distribuida; diferencias grandes requieren revisar entrada, JAR, perfil y logs.

## 7. Qué presentar

Incluye una tabla con las cinco consultas, tres tiempos por modo, mediana, CPU, RAM total y speedup. Adjunta los hashes iguales del CSV y JAR, `yarn node -list` y `hdfs dfsadmin -report` en cada perfil, y una muestra de salidas verificadas. Para demostrar distribución real, guarda una captura de YARN o JobHistory con tareas solapadas en `worker1` y `worker2`; ver los nodos registrados por sí solo no demuestra que ambos procesaron tareas.

Las tres VM comparten el hardware físico. En este CSV pequeño, el arranque de los muchos jobs —especialmente las 40 épocas de cada MLP— puede dominar el tiempo. Interpreta las mediciones reales aunque el clúster sea más lento. Para arrancar, detener o diagnosticar los perfiles, consulta [README_Hadoop.md](README_Hadoop.md).
