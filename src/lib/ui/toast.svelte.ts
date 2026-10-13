/** Short messages at the bottom of the screen, e.g. "Saved". */
export interface ToastMessage {
	id: number;
	text: string;
	kind: 'info' | 'success' | 'error';
}

let nextId = 1;

class Toasts {
	items = $state<ToastMessage[]>([]);

	show(text: string, kind: ToastMessage['kind'] = 'info', ms = 2600) {
		const id = nextId++;
		this.items = [...this.items, { id, text, kind }];
		setTimeout(() => this.dismiss(id), ms);
	}

	dismiss(id: number) {
		this.items = this.items.filter((t) => t.id !== id);
	}
}

export const toasts = new Toasts();
