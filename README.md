# Prompt Generator

A Kotlin/JVM command-line application that creates one prompt per character and
writes one text file per selected template.

## Requirements

- Java 17 or newer

## Run

From the project directory, use the included Gradle wrapper:

```shell
.\gradlew.bat run --args="--help"
.\gradlew.bat run
```

By default, the program reads `characters.txt` and `templates.json` from the
current working directory and writes files to `generated/`.

```shell
.\gradlew.bat run --args="--template classic --dry-run"
.\gradlew.bat run --args="--characters data/characters.txt --templates data/templates.json --output-dir out --overwrite"
.\gradlew.bat run --args="--interactive"
```

Use `--template NAME` repeatedly to select templates. If omitted, every
template is generated. Existing files are not replaced unless `--overwrite`
is specified. `--dry-run` validates inputs and lists the planned output files
and prompt count without writing anything.

## Input formats

`characters.txt` contains one character per line. Surrounding whitespace is
trimmed; blank lines and lines beginning with `#` are ignored. Duplicate
character names are retained and produce a warning.

`templates.json` has a `templates` object mapping names to definitions. Each
definition's `prompt` is a complete, single-line prompt and must contain
exactly one `{{character}}` placeholder. Unknown JSON fields are ignored.

```json
{
  "templates": {
    "classic": {
      "prompt": "A detailed portrait of ({{character}}:1.3)"
    },
    "cinematic": {
      "prompt": "Cinematic lighting, {{character}}, full body"
    }
  }
}
```

Output filenames are derived from template names and sanitized for portable
use. Each output file contains only prompts, one per line, in character-list
order. Input and output text uses UTF-8; generated files do not include a BOM.

## Portable Windows app

On Windows, build a portable app-image with its own Java runtime and a ZIP
archive:

```powershell
.\gradlew.bat packageWindows
```

The task requires a full JDK 17 toolchain with `jpackage` and `jmods`; Gradle
can provision the JDK toolchain if needed. It produces:

- `build\windows\PromptGenerator\PromptGenerator.exe` and the bundled runtime
- `build\windows\PromptGenerator-windows-x64.zip`

Extract the ZIP, then run `PromptGenerator.exe` from the extracted
`PromptGenerator` directory so its default `characters.txt`, `templates.json`,
and `generated\` paths resolve beside the launcher. The input files are
included in the bundle and can be edited. For example:

```powershell
cd .\PromptGenerator
.\PromptGenerator.exe --help
.\PromptGenerator.exe --output-dir generated
```

The app-image is a Windows console application. Code signing and an installer
are not included.
