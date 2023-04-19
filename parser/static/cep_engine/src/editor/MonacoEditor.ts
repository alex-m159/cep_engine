import * as monaco from "monaco-editor"

// Register a new language
monaco.languages.register({ id: "mySpecialLanguage" });

// Register a tokens provider for the language
monaco.languages.setMonarchTokensProvider("mySpecialLanguage", {
	tokenizer: {
		root: [
			[/(EVENT|WITHIN|WHERE)/, "custom-event"],
            [/type/, "custom-info"],
            [/[a-zA-Z]\(.*:.*\)/, "custom-date"]
		],

	},
});

// Define a new theme that contains only rules that match this language
monaco.editor.defineTheme("myCoolTheme", {
	base: "vs",
	inherit: false,
	rules: [
		{ token: "custom-info", foreground: "808080" },
		{ token: "custom-event", foreground: "ff0000", fontStyle: "bold" },
		{ token: "custom-notice", foreground: "FFA500" },
		{ token: "custom-date", foreground: "005500" },
	],
	colors: {
		"editor.foreground": "#000000",
	},
});

// Register a completion item provider for the new language
monaco.languages.registerCompletionItemProvider("mySpecialLanguage", {
	provideCompletionItems: (model, position) => {
		var word = model.getWordUntilPosition(position);
		var range = {
			startLineNumber: position.lineNumber,
			endLineNumber: position.lineNumber,
			startColumn: word.startColumn,
			endColumn: word.endColumn,
		};
		var suggestions = [
			{
				label: "simpleText",
				kind: monaco.languages.CompletionItemKind.Text,
				insertText: "simpleText",
				range: range,
			},
			{
				label: "testing",
				kind: monaco.languages.CompletionItemKind.Keyword,
				insertText: "testing(${1:condition})",
				insertTextRules:
					monaco.languages.CompletionItemInsertTextRule
						.InsertAsSnippet,
				range: range,
			},
			{
				label: "ifelse",
				kind: monaco.languages.CompletionItemKind.Snippet,
				insertText: [
					"if (${1:condition}) {",
					"\t$0",
					"} else {",
					"\t",
					"}",
				].join("\n"),
				insertTextRules:
					monaco.languages.CompletionItemInsertTextRule
						.InsertAsSnippet,
				documentation: "If-Else Statement",
				range: range,
			},
		];
		return { suggestions: suggestions };
	},
});



export function createEditor(container: HTMLElement, value?: string, readonly?: boolean) {
    let editor = monaco.editor.create(container, {
        theme: "myCoolTheme",
        language: "mySpecialLanguage",
        value: value,
        minimap: {
            enabled: false
        },
        readOnly: readonly
    });
    return editor
}

