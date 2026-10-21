# Guía práctica para trabajar con Hadoop y HDFS

Entorno del laboratorio MTC · Hadoop 3.4.2 · Java 11 · 19/10/2026

Esta guía es una referencia de uso diario: entrar al entorno, elegir single o multinodo, navegar en HDFS, transferir y borrar archivos, ejecutar consultas Java/MapReduce y revisar resultados. Complementa el README de operación y supone que los perfiles ya están preparados.

## 1. Entender dónde estás trabajando

En este laboratorio intervienen tres espacios diferentes:

| Espacio | Ejemplo | Cómo se maneja |
|---|---|---|
| Disco local de Pop!_OS | `~/IdeaProjects/laboratorio-mtc` | Terminal habitual, Codex, Git y Maven |
| Disco local de la VM master | `/home/master/laboratorio-mtc/datos` | SSH y comandos Linux: `ls`, `cd`, `cp`, etc. |
| HDFS del perfil activo | `/user/master/mtc/entrada` | Cliente Hadoop: `hdfs dfs -ls`, `-put`, `-get`, etc. |

**Un `ls` normal no lista HDFS. Un `cd` normal no cambia la ubicación de HDFS.** La ruta `/user/master` de HDFS tampoco es el directorio local `/home/master`.

Los bloques de HDFS se guardan físicamente en los directorios de los DataNodes, pero los archivos lógicos se administran mediante HDFS. No borres directamente los archivos de `/opt/hadoop/runtime/.../data` para eliminar un archivo de HDFS.

## 2. Conectarte y elegir el perfil

### Desde Pop!_OS: entrar a master

```bash
ssh master@192.168.56.10
```

Dentro de la VM, verifica:

```bash
whoami
hostname
```

Ambos deben mostrar `master`. Los usuarios de los trabajadores son worker1 y worker2, respectivamente:

```bash
# Estas conexiones se lanzan desde Pop!_OS si necesitas sus consolas.
ssh worker1@192.168.56.11
```

```bash
ssh worker2@192.168.56.12
```

Usa `exit` para cerrar la sesión actual y regresar al equipo desde el que te conectaste.

### Dentro de master: seleccionar multinodo

```bash
export HADOOP_CONF_DIR=/opt/hadoop/etc/hadoop
```

### Dentro de master: seleccionar single

```bash
export HADOOP_CONF_DIR=/opt/hadoop/conf-single
```

Selecciona el perfil en cada terminal nueva. Entrar con `cd /opt/hadoop/conf-single` no selecciona el perfil. Un `source ~/.bashrc` puede volver a establecer el perfil multinodo original.

### Comprobar a qué HDFS apunta el cliente

```bash
echo "$HADOOP_CONF_DIR"
hdfs getconf -confKey fs.defaultFS
hdfs getconf -confKey dfs.namenode.name.dir
hdfs getconf -confKey dfs.replication
```

| Perfil | Ruta del NameNode | Replicación |
|---|---|---:|
| Multinodo | `file:///opt/hadoop/runtime/cluster/name` | 2 |
| Single | `file:///opt/hadoop/runtime/single/name` | 1 |

La dirección `fs.defaultFS` es `hdfs://master:9000` en ambos. Como los modos se alternan y tienen almacenamiento separado, esa dirección responde con los datos del modo que esté realmente ejecutándose.

**Cambiar el perfil del cliente no cambia los procesos ya arrancados.** Primero detén el modo anterior y después arranca el nuevo, como explica el README. No uses ambos a la vez ni vuelvas a formatear para cambiar de modo.

## 3. ¿Cómo se accede a HDFS?

No necesitas abrir una sesión interactiva especial de HDFS. Cada comando `hdfs dfs` usa la configuración activa y contacta al NameNode.

```bash
hdfs dfs -ls /
```

También puedes escribir la URI completa:

```bash
hdfs dfs -ls hdfs://master:9000/
```

La URI completa especifica servidor y puerto; no selecciona por sí sola single o multinodo, porque ambos reutilizan ese servidor y puerto. El modo depende de los servicios activos.

En nuestro entorno, estas dos formas son equivalentes para la misma ruta:

```bash
hdfs dfs -ls /user/master
hadoop fs -ls /user/master
```

`hdfs dfs` utiliza la configuración del sistema de archivos; comprueba que `fs.defaultFS` sea `hdfs://master:9000` y no `file:///`.

Comprueba los servicios:

```bash
hdfs dfsadmin -report
yarn node -list
```

Single debe mostrar un DataNode y un NodeManager; multinodo, dos de cada uno. Si no hay conexión, revisa que los servicios estén arrancados antes de operar.

## 4. Navegar, listar y buscar

### Listar la raíz y tu espacio

```bash
hdfs dfs -ls /
hdfs dfs -ls /user
hdfs dfs -ls /user/master
```

### Listar con tamaños legibles o recursivamente

```bash
hdfs dfs -ls -h /user/master
hdfs dfs -ls -R /user/master/mtc
```

### Ver primero lo modificado más recientemente

```bash
hdfs dfs -ls -t /user/master/mtc/salidas
```

### Buscar archivos por nombre

```bash
hdfs dfs -find /user/master -name '*.tsv' -print
```

Las comillas protegen el comodín para que lo interprete Hadoop y no la terminal local.

### Cómo interpretar `-ls`

Las filas de archivos muestran permisos, replicación, propietario, grupo, tamaño, fecha/hora y ruta. Una entrada que comienza con `d` es un directorio; con `-`, un archivo. Los directorios no tienen un factor de replicación propio como los archivos.

### ¿Existe un `cd` dentro de HDFS?

La FS shell no proporciona una sesión persistente con `cd`. Usa rutas absolutas para evitar confusiones. Las rutas relativas de HDFS parten normalmente de `/user/<usuario>`; en nuestro caso, `/user/master`, con independencia del `pwd` local de tu terminal.

## 5. Crear directorios

Dentro de master, con el perfil previsto activo:

```bash
hdfs dfs -mkdir -p /user/master
hdfs dfs -mkdir -p /user/master/mtc/entrada
hdfs dfs -mkdir -p /user/master/mtc/salidas
hdfs dfs -mkdir -p /user/master/practica_hdfs/entrada
hdfs dfs -mkdir -p /user/master/practica_hdfs/salidas
```

`-p` crea los directorios padres que falten. Los ejemplos de borrado se limitarán a `practica_hdfs`.

## 6. Subir archivos: Pop!_OS → master → HDFS

### Paso A: preparar un directorio local de master

Desde Pop!_OS:

```bash
ssh master@192.168.56.10 'mkdir -p /home/master/laboratorio-mtc/datos'
```

### Paso B: copiar un archivo desde Pop!_OS

El nombre siguiente es un ejemplo para cuando exista tu archivo procesado:

```bash
scp datos/procesados/emergencias.tsv \
  master@192.168.56.10:/home/master/laboratorio-mtc/datos/
```

### Paso C: cargarlo desde master al HDFS activo

Dentro de master:

```bash
hdfs dfs -put \
  /home/master/laboratorio-mtc/datos/emergencias.tsv \
  /user/master/mtc/entrada/
```

Verifica:

```bash
hdfs dfs -ls -h /user/master/mtc/entrada
```

`-copyFromLocal` es equivalente a `-put`:

```bash
hdfs dfs -copyFromLocal /ruta/local/archivo.tsv /ruta/hdfs/destino/
```

La ruta local pertenece al equipo que ejecuta el cliente. Si estás conectado por SSH a master, no puedes utilizar directamente un archivo que existe solo en Pop!_OS.

### Sustituir un archivo existente

`-put` normalmente falla si el destino ya existe. Si has decidido reemplazar esa versión:

```bash
hdfs dfs -put -f \
  /home/master/laboratorio-mtc/datos/emergencias.tsv \
  /user/master/mtc/entrada/emergencias.tsv
```

Esto sobrescribe el archivo de entrada. Para comparativas reproducibles, es preferible conservar versiones identificadas y sus hashes. Carga la misma versión en el HDFS de ambos modos.

## 7. Leer contenido y revisar propiedades

### Ver un archivo pequeño completo

```bash
hdfs dfs -cat /user/master/practica_hdfs/entrada/palabras.txt
```

### Ver el principio o el final

```bash
hdfs dfs -head /user/master/mtc/entrada/emergencias.tsv
hdfs dfs -tail /user/master/mtc/entrada/emergencias.tsv
```

Estos comandos muestran aproximadamente el primer o último KiB; no reciben la opción Linux `-n` para elegir líneas.

### Ver las salidas de varios reducers

```bash
hdfs dfs -cat '/user/master/practica_hdfs/salidas/wordcount01/part-r-*'
```

Si la consulta es solo Map, puede producir `part-m-*`. Lista el directorio para identificar los archivos reales.

### Consultar tamaño, propietario y réplicas

```bash
hdfs dfs -stat 'nombre=%n bytes=%b propietario=%u replicas=%r' \
  /user/master/mtc/entrada/emergencias.tsv
```

### Comprobar existencia

```bash
hdfs dfs -test -e /user/master/mtc/entrada/emergencias.tsv
echo $?
```

El código 0 indica que la condición se cumplió. Un código distinto de cero puede indicar que no existe o un error; revisa los mensajes. Usa `-test -d` para un directorio o `-test -f` para un archivo.

## 8. Copiar, mover y renombrar dentro de HDFS

Estos ejemplos parten del archivo `palabras.txt` que se crea en la práctica del apartado 13.

### Copiar sin descargar

```bash
hdfs dfs -cp \
  /user/master/practica_hdfs/entrada/palabras.txt \
  /user/master/practica_hdfs/entrada/palabras_copia.txt
```

### Renombrar

```bash
hdfs dfs -mv \
  /user/master/practica_hdfs/entrada/palabras_copia.txt \
  /user/master/practica_hdfs/entrada/palabras_respaldo.txt
```

### Mover a otro directorio del mismo HDFS

```bash
hdfs dfs -mkdir -p /user/master/practica_hdfs/respaldo
hdfs dfs -mv \
  /user/master/practica_hdfs/entrada/palabras_respaldo.txt \
  /user/master/practica_hdfs/respaldo/
```

El comando `cp` de Linux copia archivos locales; `hdfs dfs -cp` copia dentro del sistema de archivos Hadoop indicado. Para local ↔ HDFS usa `-put` y `-get`.

## 9. Borrar archivos y directorios

**Comprueba el perfil y lista la ruta exacta antes de borrar.** Los comandos de esta sección afectan únicamente al espacio de práctica de sus ejemplos.

### Borrar un archivo

```bash
hdfs dfs -ls /user/master/practica_hdfs/respaldo/palabras_respaldo.txt
hdfs dfs -rm /user/master/practica_hdfs/respaldo/palabras_respaldo.txt
```

### Borrar un directorio vacío

```bash
hdfs dfs -rmdir /user/master/practica_hdfs/respaldo
```

### Borrar un directorio con contenido

Solo cuando quieras descartar expresamente esa salida de prueba:

```bash
hdfs dfs -ls -R /user/master/practica_hdfs/salidas/wordcount01
hdfs dfs -rm -r /user/master/practica_hdfs/salidas/wordcount01
```

`-r` elimina recursivamente el directorio y su contenido. Para repetir una consulta normalmente es mejor usar `wordcount02`, `run02`, etc. y conservar las salidas anteriores.

### ¿Se puede recuperar lo borrado?

Consulta la configuración de papelera:

```bash
hdfs getconf -confKey fs.trash.interval
```

Por defecto la papelera está desactivada. Si el valor efectivo es 0, no cuentes con recuperar archivos mediante Trash. Con una papelera habilitada, `-rm` puede moverlos a `.Trash`; debes revisar la política y retención configuradas. `-skipTrash` omite la papelera y no hace falta para el uso diario.

No uses el borrado local de `/opt/hadoop/runtime`, ni el formateo del NameNode, como sustituto de `hdfs dfs -rm`. Esas operaciones afectan al almacenamiento o metadatos del sistema completo.

## 10. Descargar resultados y exportarlos

### Descargar de HDFS al disco local de master

```bash
mkdir -p ~/resultados-hadoop
hdfs dfs -get \
  /user/master/practica_hdfs/salidas/wordcount01 \
  /home/master/resultados-hadoop/wordcount01
```

Conserva los distintos `part-r-*` y el resto de la carpeta. Utiliza un destino local nuevo si ya descargaste otra versión. `-copyToLocal` es equivalente a `-get`.

### Reunir las partes en un archivo local

```bash
hdfs dfs -getmerge \
  '/user/master/practica_hdfs/salidas/wordcount01/part-r-*' \
  /home/master/resultados-hadoop/wordcount01.tsv
```

`getmerge` concatena las partes; no agrega encabezados ni garantiza un orden global por clave. Los archivos de TextOutputFormat normalmente ya terminan cada registro con un salto de línea. La opción `-nl` añade un salto al final de cada archivo y puede introducir líneas vacías si ya lo tenía.

Para el ejemplo WordCount, puedes generar un TSV con encabezado:

```bash
printf 'palabra\tcantidad\n' > /home/master/resultados-hadoop/wordcount01_con_encabezado.tsv
hdfs dfs -cat '/user/master/practica_hdfs/salidas/wordcount01/part-r-*' \
  >> /home/master/resultados-hadoop/wordcount01_con_encabezado.tsv
```

Para C1/C2/ML usa los encabezados que correspondan al esquema real de salida. Si el comando de lectura falla, no trates el archivo parcialmente escrito como un resultado completo.

### Descargar desde master a Pop!_OS

Desde una terminal de Pop!_OS:

```bash
scp master@192.168.56.10:/home/master/resultados-hadoop/wordcount01_con_encabezado.tsv .
```

Para descargar una carpeta completa:

```bash
scp -r master@192.168.56.10:/home/master/resultados-hadoop/wordcount01 .
```

Los archivos exportados pueden importarse posteriormente en Power BI desde Windows. Hadoop no necesita estar activo mientras trabajas con esas copias.

## 11. Espacio, bloques y permisos

### Capacidad disponible en HDFS

```bash
hdfs dfs -df -h /
```

### Tamaño de una carpeta

```bash
hdfs dfs -du -s -h /user/master/mtc
```

En HDFS, `du` muestra tamaño lógico y espacio consumido con réplicas, además de la ruta. No confundas esos datos con el disco libre de Ubuntu:

```bash
df -h /
```

### Contar directorios, archivos y bytes

```bash
hdfs dfs -count -h /user/master/mtc
```

### Ver dónde se almacenan los bloques

```bash
hdfs fsck /user/master/practica_hdfs/entrada -files -blocks -locations
```

Permite comprobar salud, réplicas y ubicaciones. Esta consulta inspecciona el estado; no añade opciones de reparación ni eliminación.

### Revisar o cambiar permisos

```bash
hdfs dfs -ls -d /user/master/practica_hdfs
hdfs dfs -chmod 750 /user/master/practica_hdfs
```

Para el propietario: lectura, escritura y acceso; para el grupo: lectura y acceso; para otros: ninguno. Permisos y propietarios pertenecen a HDFS, no son los permisos del directorio local de la VM.

En nuestro montaje sin Kerberos, el usuario que ejecuta el cliente influye en la identidad HDFS. El administrador HDFS corresponde a la cuenta que ejecuta el NameNode, aquí master. `sudo hdfs ...` no es la forma habitual de resolver permisos: cambia el usuario local y puede ni siquiera conservar el PATH.

Si realmente necesitas corregir la propiedad de tu carpeta de práctica, como administrador HDFS:

```bash
hdfs dfs -chown master:master /user/master/practica_hdfs
```

Evita cambiar permisos o propietario recursivamente sobre todo HDFS para resolver un problema de una ruta concreta.

## 12. Ejecutar tus consultas Java/MapReduce

En este laboratorio, una «consulta» es un programa Java con uno o más jobs MapReduce. El comando HDFS no ejecuta SQL. Nuestro entorno no incluye automáticamente una capa SQL como Hive.

### Desarrollo en Pop!_OS

El proyecto, JAR y clase de este apartado son nombres de ejemplo: adáptalos a tu código real.

```bash
cd ~/IdeaProjects/laboratorio-mtc
mvn clean package
```

Compila para Java 11 con dependencias Hadoop 3.4.2. Copia el JAR:

```bash
ssh master@192.168.56.10 'mkdir -p /home/master/laboratorio-mtc/jars'
scp target/laboratorio-mtc-1.0-SNAPSHOT.jar \
  master@192.168.56.10:/home/master/laboratorio-mtc/jars/
```

### Ejecución dentro de master

1. Comprueba que los servicios del modo previsto estén activos.
2. Selecciona su perfil en esta terminal.
3. Comprueba la entrada y usa una salida nueva.

Ejemplo multinodo:

```bash
export HADOOP_CONF_DIR=/opt/hadoop/etc/hadoop
hdfs dfs -ls /user/master/mtc/entrada
hadoop jar \
  /home/master/laboratorio-mtc/jars/laboratorio-mtc-1.0-SNAPSHOT.jar \
  pe.edu.uni.cc531.mtc.Consulta01Driver \
  /user/master/mtc/entrada/emergencias.tsv \
  /user/master/mtc/salidas/c1_cluster_run01
```

En single utiliza `/opt/hadoop/conf-single` y una salida como `c1_single_run01`, después de cambiar efectivamente los servicios. No necesitas copiar el JAR a cada trabajador: Hadoop distribuye el programa a las tareas. Si hay bibliotecas adicionales, debes empaquetarlas o distribuirlas con el mecanismo apropiado.

| Parte del comando | Significado |
|---|---|
| `hadoop jar` | Ejecuta una aplicación empaquetada en un JAR |
| Ruta `.jar` | Archivo en el disco local de master |
| Clase completa | Clase con el método `main` que coordina el trabajo |
| Argumentos posteriores | Los interpreta tu driver: en el ejemplo, entrada y salida HDFS |

Cada driver puede tener parámetros distintos. El README del proyecto debe documentarlos. En C1 y C2, un driver puede lanzar los tres jobs secuenciales; las salidas intermedias también deben tener rutas diferenciadas por corrida. Los procesos Map/Reduce se ejecutarán donde YARN los asigne.

### Conservar el log del driver

Para una ejecución habitual con salida en pantalla y archivo:

```bash
mkdir -p ~/logs-consultas
set -o pipefail
hadoop jar \
  /home/master/laboratorio-mtc/jars/laboratorio-mtc-1.0-SNAPSHOT.jar \
  pe.edu.uni.cc531.mtc.Consulta01Driver \
  /user/master/mtc/entrada/emergencias.tsv \
  /user/master/mtc/salidas/c1_cluster_run02 \
  2>&1 | tee ~/logs-consultas/c1_cluster_run02.log
```

`pipefail` evita que el éxito de `tee` oculte un fallo del proceso Hadoop en el estado de la tubería. El driver debe devolver error si falla alguna etapa. Para la comparación formal, utiliza el recolector de métricas: ya guarda `driver.log` y mide el comando completo.

## 13. Práctica completa con el WordCount incluido

Esta práctica funciona sin crear un proyecto Java. Usa el JAR de ejemplos instalado. Todos los comandos van dentro de master, con el modo previsto activo.

### Crear una entrada local y subirla

```bash
mkdir -p ~/practica-hdfs-local
printf '%s\n' 'hadoop hadoop hdfs' 'hdfs java' > ~/practica-hdfs-local/palabras.txt
hdfs dfs -mkdir -p /user/master/practica_hdfs/entrada
hdfs dfs -mkdir -p /user/master/practica_hdfs/salidas
hdfs dfs -put ~/practica-hdfs-local/palabras.txt /user/master/practica_hdfs/entrada/
```

Si ya existe, revisa su contenido antes de decidir reemplazarlo. Evita dejar copias de respaldo dentro de `entrada`, porque WordCount leerá todos sus archivos de datos y contaría esas copias también.

### Ejecutar

```bash
hadoop jar \
  /opt/hadoop/share/hadoop/mapreduce/hadoop-mapreduce-examples-3.4.2.jar \
  wordcount \
  -Dmapreduce.job.reduces=2 \
  /user/master/practica_hdfs/entrada \
  /user/master/practica_hdfs/salidas/wordcount01
```

### Revisar

```bash
hdfs dfs -ls /user/master/practica_hdfs/salidas/wordcount01
hdfs dfs -cat '/user/master/practica_hdfs/salidas/wordcount01/part-r-*' | sort
```

Resultado esperado para la entrada original, sin archivos adicionales:

```text
hadoop  2
hdfs    2
java    1
```

La salida contiene normalmente `_SUCCESS` y archivos `part-r-*`. `_SUCCESS` indica finalización del job, no demuestra por sí solo que el algoritmo responda correctamente a la pregunta. Verifica también los valores. Para repetir, usa `wordcount02`.

## 14. Seguir, diagnosticar o cancelar aplicaciones

Dentro de master, con el perfil correcto:

### Listar aplicaciones

```bash
yarn application -list
yarn application -list -appStates ALL
```

El ID real aparece al lanzar la aplicación, con un formato como `application_1234567890000_0001`. El siguiente ID es solo ilustrativo: sustitúyelo.

### Consultar una aplicación concreta

```bash
yarn application -status application_1234567890000_0001
```

Revisa estado, estado final y diagnósticos. Para confirmar éxito, busca un estado final `SUCCEEDED`; que el trabajo haya dejado de ejecutarse no implica que terminara correctamente.

### Descargar logs agregados

```bash
mkdir -p ~/logs-consultas
yarn logs -applicationId application_1234567890000_0001 \
  > ~/logs-consultas/application_1234567890000_0001.log 2>&1
```

Pueden estar disponibles tras finalizar, según la agregación configurada. Si no aparecen, revisa la interfaz del NodeManager, sus logs locales y el log del driver.

### Cancelar una aplicación que tú has decidido detener

```bash
yarn application -kill application_1234567890000_0001
```

Confirma el ID antes de ejecutarlo y consulta después su estado. Ctrl+C en el cliente no garantiza por sí solo que ya no exista una aplicación activa en YARN. Si tu driver encadena jobs, detén también ese driver para evitar que envíe etapas posteriores; revisa todas las aplicaciones que lanzó.

## 15. Medir consultas y guardar evidencias

El paquete `metricas_hadoop.zip` entregado antes contiene `medir_hadoop.py` y su guía detallada. El script se ejecuta en master y usa SSH para medir trabajadores cuando corresponde. Este documento no incorpora ese archivo.

Ejemplo con el WordCount de práctica, en multinodo:

```bash
export HADOOP_CONF_DIR=/opt/hadoop/etc/hadoop
python3 /home/master/metricas-hadoop/medir_hadoop.py medir \
  --modo cluster --trabajo WORDCOUNT --repeticion 1 \
  --base /home/master/mediciones-mtc-v1 \
  -- /opt/hadoop/bin/hadoop jar \
  /opt/hadoop/share/hadoop/mapreduce/hadoop-mapreduce-examples-3.4.2.jar \
  wordcount -Dmapreduce.job.reduces=2 \
  /user/master/practica_hdfs/entrada \
  /user/master/practica_hdfs/salidas/medicion_cluster_01
```

Para single, cambia realmente los servicios, activa su configuración, utiliza `--modo single` y otra salida. La opción `--modo` del recolector no arranca, detiene ni cambia Hadoop.

Para consultas propias sustituye WordCount por el comando completo del driver y usa las etiquetas C1, C2, DBSCAN, MLP_C o MLP_R. Conserva datos, JAR y parámetros; realiza las repeticiones con salidas nuevas. WordCount sirve como prueba y no sustituye las consultas del parcial.

Genera las tablas después de medir ambos modos:

```bash
python3 /home/master/metricas-hadoop/medir_hadoop.py comparar \
  --base /home/master/mediciones-mtc-v1
```

Los promedios salen de los CSV de muestreo; las capturas de `htop` son evidencias visuales. Incluye nodos registrados, CPU/RAM por nodo durante los trabajos, intentos Map/Reduce y resultados verificados. Para demostrar paralelismo, observa tareas de ambos trabajadores con tiempos solapados.

## 16. Interfaces web y servicios

Desde el navegador de Pop!_OS:

| Interfaz | URL | Qué mirar |
|---|---|---|
| HDFS | http://192.168.56.10:9870 | DataNodes, capacidad y explorador de archivos |
| YARN | http://192.168.56.10:8088 | Nodos y aplicaciones |
| JobHistory | http://192.168.56.10:19888 | Jobs terminados, tareas e intentos |

El puerto 9000 de nuestra configuración es para el cliente HDFS, no la página web. No uses `http://...:9870` como si fuera una URI `hdfs://...:9000`.

El explorador web sirve para revisar las rutas de HDFS. Para los procedimientos reproducibles de esta guía, utiliza la CLI. Si los enlaces de YARN contienen nombres como worker1, Pop!_OS debe resolverlos mediante sus entradas en `/etc/hosts`.

### Arranque con el perfil seleccionado y el modo anterior detenido

```bash
start-dfs.sh
start-yarn.sh
mapred --daemon start historyserver
```

### Detención con el mismo perfil con que se arrancaron

```bash
mapred --daemon stop historyserver
stop-yarn.sh
stop-dfs.sh
```

**HistoryServer se detiene por separado**: `stop-dfs.sh` y `stop-yarn.sh` no lo detienen.

Si indica que HistoryServer ya existe y menciona una carpeta PID de otro modo, identifica primero qué instancia quedó activa:

```bash
jps -lv
echo "$HADOOP_CONF_DIR"
grep -nE '^export HADOOP_(LOG|PID)_DIR=' "$HADOOP_CONF_DIR/hadoop-env.sh"
```

En single, LOG/PID deben apuntar a `/runtime/single/`; en multinodo, a `/runtime/cluster/`. Detén la instancia anterior con su perfil antes de arrancar la siguiente. No borres su PID mientras el proceso siga ejecutándose.

## 17. Errores habituales

| Mensaje o situación | Acción |
|---|---|
| `No such file or directory` | Distingue ruta local/HDFS y revisa el perfil. Lista el directorio padre. |
| Archivo visible en multinodo pero ausente en single | Son dos HDFS distintos; incorpora la misma entrada al otro perfil. |
| `FileAlreadyExistsException` al ejecutar | Usa una salida nueva; no crees previamente la carpeta final del job. |
| `Permission denied` | Revisa usuario, propietario y permisos de la ruta; no recurras automáticamente a sudo o chmod 777. |
| `Connection refused` | Verifica NameNode/ResourceManager, perfil y logs. |
| `SafeModeException` | Consulta `hdfs dfsadmin -safemode get`, DataNodes y logs; no fuerces la salida sin entender por qué está en modo seguro. |
| Trabajo en espera | Revisa NodeManagers RUNNING y recursos; el ApplicationMaster también consume memoria. |
| `ClassNotFoundException` | Revisa clase principal, JAR, dependencias y configuración MapReduce. |
| Proceso en `jps` pero nodo ausente | Examina sus logs y conectividad; proceso iniciado no equivale a registro exitoso. |
| Contenido inesperadamente duplicado | Comprueba si dejaste copias del dataset en el directorio de entrada. |
| Resultado mostrado en varias partes | Es normal con varios reducers; usa `-cat` con el patrón o `-getmerge`. |

## 18. Chuleta de uso diario

Todos los comandos siguientes se ejecutan en master con el perfil correcto. Las rutas abreviadas son ejemplos, no comandos para copiar literalmente sin adaptarlos.

| Quiero… | Comando |
|---|---|
| Ver perfil | `echo "$HADOOP_CONF_DIR"` |
| Ver destino HDFS | `hdfs getconf -confKey fs.defaultFS` |
| Ver la raíz | `hdfs dfs -ls /` |
| Listar carpeta | `hdfs dfs -ls /ruta` |
| Listar recursivamente | `hdfs dfs -ls -R /ruta` |
| Crear carpeta | `hdfs dfs -mkdir -p /ruta` |
| Subir archivo local | `hdfs dfs -put archivo_local /destino/` |
| Leer archivo | `hdfs dfs -cat /ruta/archivo` |
| Ver primeros bytes | `hdfs dfs -head /ruta/archivo` |
| Copiar dentro de HDFS | `hdfs dfs -cp /origen /destino` |
| Mover o renombrar | `hdfs dfs -mv /origen /destino` |
| Borrar archivo elegido | `hdfs dfs -rm /ruta/archivo` |
| Borrar carpeta vacía | `hdfs dfs -rmdir /ruta/carpeta` |
| Borrar carpeta y contenido elegido | `hdfs dfs -rm -r /ruta/carpeta` |
| Descargar de HDFS | `hdfs dfs -get /origen destino_local` |
| Reunir archivos de salida | `hdfs dfs -getmerge /salida resultado_local.tsv` |
| Ver consumo | `hdfs dfs -du -s -h /ruta` |
| Ver capacidad HDFS | `hdfs dfs -df -h /` |
| Ver DataNodes | `hdfs dfsadmin -report` |
| Ver trabajadores YARN | `yarn node -list` |
| Ver aplicaciones | `yarn application -list` |
| Ejecutar consulta | `hadoop jar archivo.jar paquete.Clase entrada salida` |
| Ayuda de un comando | `hdfs dfs -help rm` |

## Fuentes y alcance

- [Apache Hadoop 3.4.2: comandos del sistema de archivos](https://hadoop.apache.org/docs/r3.4.2/hadoop-project-dist/hadoop-common/FileSystemShell.html)
- [Apache Hadoop 3.4.2: comandos YARN](https://hadoop.apache.org/docs/r3.4.2/hadoop-yarn/hadoop-yarn-site/YarnCommands.html)
- [Tutorial MapReduce](https://hadoop.apache.org/docs/r3.4.2/hadoop-mapreduce-client/hadoop-mapreduce-client-core/MapReduceTutorial.html)
- [Un nodo con YARN](https://hadoop.apache.org/docs/r3.4.2/hadoop-project-dist/hadoop-common/SingleCluster.html)

Los ejemplos están adaptados a tus IP, usuarios y perfiles acordados. No se ha operado ni borrado ningún archivo de tus VM desde aquí. Las clases de las consultas propias, su esquema de datos y resultados siguen dependiendo de tu implementación; el WordCount utiliza el ejemplo incluido en la distribución.
