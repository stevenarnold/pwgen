## NAME

`pwgen` - a utility for generating passwords based on different policies

## SYNPOSIS

`pwgen [generate|fetch] <options>`

## DESCRIPTION

pwgen is **not yet complete**.  Portions of the description that are not yet implemented are commented using _italics_ notation.

pwgen is a command-line tool for generating passwords according to different policies.  Using command-line options, many different policies can be used, including such options as password length, use of numeric digits, capital letters, and punctuation.  _Users have the option of saving their passwords, along with metadata, by associating it with a tag, which can be retrieved with the entire tag or part of it._  Users can opt, when retrieving a password, to send it directly to their clipboard for pasting into an application, such as a web browser.

_Metadata can be tagged.  By default the information selected from a record is the password, but the user can optionally choose other data, and in fact the user is not required to store a password field at all.  This may be useful for storing information such as a credit card number, billing address, etc._

_User data is stored on disk using strong encryption.  The user may set the caching policy, up to and including immediate removal of the keystore password for each usage._

## INSTALLATION

Download from https://github.com/stevenarnold/pwgen.

## ENVIRONMENT

pwgen requires a JVM version 1.6 or higher.  It can be run as follows:

`$ java -jar pwgen.jar [args]`

## OPTIONS

See documentation for built-in character classes below.  The basic subcommands for `pwgen` include:

  * `generate [options] [pairs]`
    This will generate a password using the given options.  If a profile is specified, the options given on the command-line will override any options in that profile with the ones provided.  If the options include an assertion to save the profile, any provided pairs will be saved with it.  If specified in the options, the generated password will be pushed to the clipboard.
    * `-c [<tag>] [<names>] [<charset>]` or `--charset [<tag>] [<names>] [<charset>]`
      Defines the normal character set to be used when selecting characters for the password.  The default is `all-chars`.  A tag  may be specified, which can then be used as a character class name elsewhere in the invocation.  Note that the definition of `charset` does not affect the definition of  `special-charset`, `initial-charset`, or `ending-charset`, unless the tag defined with charset is used in these character classes, or unless the `--global` flag is used, which causes all the other character classes to use `charset` by default.
    * `-m <number>` or `--max <number>`
      The maximum number of characters allowed for the password.
    * `-n <number>` or `--min <number>`
      The minimum number of characters allowed for the password.
    * `-md <number>` or `--max-numbers <number>`
      The maximum number of numeric digits in the password.  If both min and max values are used, the program picks a random number of characters between the two (inclusive) that must be numeric.  If `--max-numbers` is less than `--min-numbers`, pwgen displays an error and quits.
    * `-nd <number>` or `--min-numbers <number>`
      The minimum number of numeric digits in the password.  See also the description of `--max-numbers`.
    * `-mc <number>` or `--max-capitals <number>`
      The maximum number of capital letters to use in the password.    If both min and max values are used, the program picks a random number of characters between the two (inclusive) that must be capital letters.  If `--max-capitals` is less than `--min-capitals`, pwgen displays an error and quits.
    * `-nc <number>` or `--min-capitals <number>`
      The minimum number of capitals letters to use in the password.  See also the description of `--max-capitals`.
    * `-ms <number>` or `--max-special <number>`
      The maximum number of 'special' characters to use in the password.  Special characters are punctuation symbols on a standard U.S. keyboard and do not include Unicode characters such as umlauts or accented characters.  If both min and max values are used, the program picks a random number of characters between the two (inclusive) that must be special characters.  If `--max-special` is less than `--min-special`, pwgen displays an error and quits.
    * `-ns <number>` or `--min-special <number>`
      The minimum number of 'special' characters to use in the password.  See also the description of `--max-special`.
    * `-sc <charset>` or `--special-charset <charset>`
      Use the given string as the "special" or punctuation characters in the generated password, instead of the standard charset 'special'.  Other standard charset names (such as 'numeric') may also be used.
    * `-ic <charset>` or `--initial-charset <charset>`
      Require that a character in the given string be the initial character of the password.  Other standard charset names (such as 'alpha') may also be used.
    * `-ec <charset>` or `--ending-charset <charset>`
      Require that a character in the given string be the ending or final character of the password.  Other standard charset names (such as 'alphanumeric') may also be used.
    * `-as` or `--allow-spaces`
      Include the space character in the charset used to pick passwords, and do not remove spaces if a dictionary word contains one. However, a space cannot be the first or last character of a password. 
    * `-cp <name>` or `--create-profile <name>`
      Save a profile with this particular set of options.  If the profile already exists, the `--force` flag must be used to overwrite the profile, or the program will exit with an error.  The profile's options are defined as the profile identified by `--use-profile`, if applicable, overlaid with the command-line options provided.
    * `-f` or `--force`
      If `--create-profile` is specified, and a profile of that name already exists, the `--force` flag will silently replace it with the new profile being defined.  If a profile of the same name does not exist, the `--force` flag, if present, has no effect.
    * `-up <name>` or `--use-profile <name>`
      Use the specified profile, overlaid with any command-line options that are provided.  If the profile does not exist, a warning is printed to STDERR.
    * `-g` or `--global`
      Use the character class defined with the `--charset` option for all character classes, including special, initial and ending character sets.  This flag is ignored unless `--charset` is defined.  Note that even if this flag is used, if one or more of the secondary character sets are defined, they will override this setting.  For example, if `--special-charset "-_."` is defined, the `initial-charset` and `ending-charset` classes will match the `charset` definition, but `special-charset` will be defined as per the command-line specification.
    * `-r` or `--memorable <pct>`
      If an alphabetic (upper or lower) character is randomly chosen, there is a 'pct' chance that it will be replaced by a whole or partial dictionary word.  This will lengthen the candidate password by (most likely) more than one character.  This word selection may be mutated by the other rules; for example, a requirement to include four digits in the password may cause some of the characters in the word to be changed to digits.

##Possible Future 'generate' Flags##
>   **--password or -p**: Use the given password to modify or use a profile.  Default is to ask the user interactively in a way that does not echo the password to the screen.
<br /><br />
>   **--dict or -d**: Use the given file as the dictionary for memorable passwords, one word per line.  This option has no effect if the '--memorable' flag is not used.
    
- *fetch [options] &lt;profile-tag&gt;[:meta-tag]: This will fetch either the password for the given profile, or the data associated with the given meta-tag in the profile.  If the password for the profile is not cached, the user will be asked for the password.*

## Character Classes

The following character classes are supported out-of-the-box by `pwgen`.  You may craft other ones at the command-line using the `--charset` option.  Character classes are considered "joined" if they are included together in square brackets [].  Any of these can be used directly at the command line when you invoke pwgen.

>    **alpha-lower** => "abcdefghijklmnopqrstuvwxyz"
<br />
>    **right-alpha-lower** => "hynmjuiklop"
<br />
>    **left-alpha-lower** => "qazwsxedcrfvtgb"
<br />
>    **alpha-upper** => "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
<br />
>    **alpha** => [alpha-lower alpha-upper]
<br />
>    **numeric** => "0123456789"
<br />
>    **left-numeric** => "09876"
<br />
>    **right-numeric** => "12345"
<br />
>    **alphanumeric** => [alpha numeric]
<br />
>    **special** => "~\`!@#$%^&*()-_=+]}[{;:,<.>/?'|"
<br />
>    **special-nocaps** => "`-=;',./[]"
<br />
>    **right-special-nocaps** => ",./;'[]-="
<br />
>    **right-nocaps** => [right-alpha-lower right-numeric right-special-nocaps]
<br />
>    **all-chars** => [alphanumeric special]
<br />
>    **all-chars-with-space** => [all-chars " "]
<br />
>    **all-noshift-chars** => [alpha-lower numeric special-nocaps]

## Examples

>   $ *pwgen generate --max 30 --min 20 --memorable 95 -nc 1 -ns 2 -md 4 -nd 1 --allow-spaces 50 -sc special*
<br /><br/>
>   Generate a password of between 20 and 30 characters inclusive, with at least one capital letter, two special characters (symbol), at least one and no more than four digits, using the 'special' character set for specials, using dictionary words with 95 percent odds, and permitting spaces.  Example output: "D3T4oratieli3Su(instTn-eerac"
<br /><br/>
>   $ *pwgen generate --use-profile standard*
<br /><br/>
>   Generate a password using the standard profile.  The standard profile is a profile generated by pwgen by default when the program is first run.  It represents a "reasonable" set of rules that will work with most password algorithms.

### Bugs

...

## License

Copyright © 2013 Arnold Software Associates and Steven D. Arnold

Distributed under the Eclipse Public License, the same as Clojure.
